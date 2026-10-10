package com.plantdesk.security;

import com.plantdesk.config.PlantDeskProperties;
import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.support.TestData;
import com.plantdesk.support.TestData.Plant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityHardeningIT extends AbstractIntegrationTest {

    private static final Set<String> PUBLIC_API = Set.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Autowired
    PlantDeskProperties props;

    /**
     * Server-side authorisation cannot be forgotten: any new /api endpoint without a
     * @PreAuthorize (on the method or its controller) fails this test.
     */
    @Test
    void everyApiEndpointDeclaresWhichRolesMayCallIt() {
        var unguarded = mappings.getHandlerMethods().entrySet().stream()
                .filter(e -> e.getKey().getPatternValues().stream().anyMatch(p -> p.startsWith("/api/")))
                .filter(e -> !PUBLIC_API.containsAll(e.getKey().getPatternValues()))
                .filter(e -> !guarded(e.getValue()))
                .map(e -> e.getKey().toString())
                .toList();
        assertThat(unguarded).as("endpoints without @PreAuthorize").isEmpty();
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/").secure(true))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("unsafe-inline"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
    }

    @Test
    void noCorsHeadersAreOfferedToForeignOrigins() throws Exception {
        mvc.perform(get("/api/work-orders").header("Origin", "https://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void errorBodiesDoNotLeakInternals() throws Exception {
        mvc.perform(get("/api/work-orders/{id}", "not-a-uuid").header("Authorization", bearer(data.newPlant().manager())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))));
    }

    @Test
    void repeatedFailures_onOneAccount_areThrottled_evenFromManyIps() throws Exception {
        Plant p = data.newPlant();
        String email = p.manager().getEmail();
        for (int i = 0; i < props.rateLimit().accountFailures(); i++) {
            login(p.code(), email, "wrong-password-" + i, "198.51.100." + i).andExpect(status().isUnauthorized());
        }
        // Even the correct password is refused until the window passes — the attacker cannot
        // learn that their next guess was right.
        login(p.code(), email, TestData.PASSWORD, "198.51.100.250")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail", containsString("Too many sign-in attempts")));
    }

    @Test
    void oneIp_isThrottled_acrossManyAccounts() throws Exception {
        String ip = "203.0.113.77";
        for (int i = 0; i < props.rateLimit().loginPerIp(); i++) {
            login("NOPLANT", "nobody" + UUID.randomUUID() + "@x.test", "whatever-123", ip).andExpect(status().isUnauthorized());
        }
        login("NOPLANT", "another@x.test", "whatever-123", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // A different IP is unaffected.
        login("NOPLANT", "another@x.test", "whatever-123", "203.0.113.78").andExpect(status().isUnauthorized());
    }

    private ResultActions login(String plant, String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"plantCode\":\"" + plant + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private static boolean guarded(HandlerMethod m) {
        return AnnotatedElementUtils.hasAnnotation(m.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(m.getBeanType(), PreAuthorize.class);
    }
}
