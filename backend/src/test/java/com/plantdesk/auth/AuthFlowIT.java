package com.plantdesk.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.support.TestData;
import com.plantdesk.support.TestData.Plant;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper json;

    Plant plant;

    @BeforeEach
    void setUp() {
        plant = data.newPlant();
    }

    @Test
    void login_returnsWorkingTokenPair() throws Exception {
        MvcResult result = login(plant.manager().getEmail(), TestData.PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.plantCode").value(plant.code()))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andReturn();

        String access = body(result).get("accessToken").asText();
        assertThat(refreshCookie(result)).isNotBlank();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(plant.manager().getEmail()))
                .andExpect(jsonPath("$.role").value("MAINTENANCE_MANAGER"));
    }

    @Test
    void wrongPassword_unknownEmail_andWrongPlant_allGiveTheSame401() throws Exception {
        login(plant.manager().getEmail(), "wrong-password").andExpect(status().isUnauthorized());
        login("nobody@nowhere.test", TestData.PASSWORD).andExpect(status().isUnauthorized());
        Plant other = data.newPlant();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(other.code(), plant.manager().getEmail(), TestData.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_rotatesTheToken() throws Exception {
        String first = refreshCookie(login(plant.tech1().getEmail(), TestData.PASSWORD).andReturn());

        MvcResult rotated = refresh(first).andExpect(status().isOk()).andReturn();
        String second = refreshCookie(rotated);

        assertThat(second).isNotBlank().isNotEqualTo(first);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + body(rotated).get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedToken_revokesTheWholeFamily() throws Exception {
        String first = refreshCookie(login(plant.tech1().getEmail(), TestData.PASSWORD).andReturn());
        String second = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());

        // Attacker (or a stale tab) replays the old token.
        refresh(first).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("reuse detected")));

        // The legitimate holder of the newest token is signed out too: the family is dead.
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesTheRefreshToken() throws Exception {
        String token = refreshCookie(login(plant.admin().getEmail(), TestData.PASSWORD).andReturn());
        mvc.perform(post("/api/auth/logout").cookie(new Cookie(AuthController.REFRESH_COOKIE, token)))
                .andExpect(status().isNoContent());
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUser_cannotRefresh() throws Exception {
        String token = refreshCookie(login(plant.tech2().getEmail(), TestData.PASSWORD).andReturn());
        mvc.perform(post("/api/users/{id}/deactivate", plant.tech2().getId()).header("Authorization", bearer(plant.admin())))
                .andExpect(status().isOk());
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void accessToken_expiresAfter15Minutes() throws Exception {
        clock.set(java.time.Instant.now());
        String access = "Bearer " + body(login(plant.viewer().getEmail(), TestData.PASSWORD).andReturn()).get("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", access)).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(16));
        mvc.perform(get("/api/auth/me").header("Authorization", access)).andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrForgedToken_is401() throws Exception {
        mvc.perform(get("/api/work-orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/work-orders").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
        String valid = bearer(plant.admin());
        String tampered = valid.substring(0, valid.length() - 4) + "AAAA";
        mvc.perform(get("/api/work-orders").header("Authorization", tampered)).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(plant.code(), email, password)));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(new Cookie(AuthController.REFRESH_COOKIE, token)));
    }

    private static String loginJson(String plantCode, String email, String password) {
        return "{\"plantCode\":\"" + plantCode + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String refreshCookie(MvcResult result) {
        Cookie c = result.getResponse().getCookie(AuthController.REFRESH_COOKIE);
        return c == null ? null : c.getValue();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
