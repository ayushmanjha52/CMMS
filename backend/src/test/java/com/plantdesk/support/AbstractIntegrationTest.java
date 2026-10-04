package com.plantdesk.support;

import com.plantdesk.security.AuthenticatedUser;
import com.plantdesk.security.JwtService;
import com.plantdesk.user.User;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for every integration test. All subclasses share one Spring context (same config),
 * so the schema is migrated once and each test creates its own uniquely-coded tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class AbstractIntegrationTest {

    public static final String APP_ROLE = "plantdesk_app";
    public static final String APP_PASSWORD = "plantdesk_app_test";

    /**
     * NOT @ServiceConnection. That would point the application at the container's superuser,
     * and superusers bypass row-level security — every RLS assertion would pass for the wrong
     * reason. The app connects as the restricted role; only Flyway uses the owner.
     */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        DbTarget db = DbTarget.get();
        registry.add("spring.datasource.url", db::jdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", db::ownerUser);
        registry.add("spring.flyway.password", db::ownerPassword);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestData data;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JwtService jwtService;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    protected String bearer(User user) {
        var principal = new AuthenticatedUser(user.getId(), user.getTenantId(), user.getRole(), user.getEmail(), user.getFullName());
        return "Bearer " + jwtService.issue(principal).value();
    }
}
