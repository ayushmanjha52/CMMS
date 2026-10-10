package com.plantdesk.demo;

import com.plantdesk.support.AbstractIntegrationTest;
import com.plantdesk.tenancy.SystemLookupDao;
import com.plantdesk.user.User;
import com.plantdesk.user.UserRepository;
import com.plantdesk.workorder.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public demo seeds itself, cannot be locked out, and resets cleanly to the same state. */
@TestPropertySource(properties = "plantdesk.demo.enabled=true")
class DemoResetIT extends AbstractIntegrationTest {

    @Autowired SystemLookupDao lookup;
    @Autowired DemoResetJob resetJob;
    @Autowired WorkOrderRepository workOrders;
    @Autowired UserRepository users;

    @Test
    void demoAccountsCannotBeDeactivated() throws Exception {
        UUID demo = lookup.findTenantIdByCode("DEMO").orElseThrow();
        User admin = data.as(demo, () -> users.findAllByOrderByFullNameAsc().stream()
                .filter(u -> u.getEmail().equals("admin@demo.plant")).findFirst().orElseThrow());
        User manager = data.as(demo, () -> users.findAllByOrderByFullNameAsc().stream()
                .filter(u -> u.getEmail().equals("manager@demo.plant")).findFirst().orElseThrow());

        mvc.perform(post("/api/users/{id}/deactivate", manager.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Demo accounts can't be deactivated")));
    }

    @Test
    void nightlyResetRestoresTheSameDemoUnderANewTenant() {
        UUID before = lookup.findTenantIdByCode("DEMO").orElseThrow();
        long ordersBefore = data.as(before, () -> workOrders.count());
        UUID otherPlant = data.newPlant().tenantId();
        long otherBefore = data.as(otherPlant, () -> users.count());

        resetJob.reset();

        UUID after = lookup.findTenantIdByCode("DEMO").orElseThrow();
        assertThat(after).isNotEqualTo(before);
        assertThat(data.as(after, () -> workOrders.count())).isEqualTo(ordersBefore);
        assertThat(lookup.findTenantIdByCode("LOCO")).isPresent();
        // The old tenant is gone, and a non-demo plant was not touched.
        assertThat(lookup.allTenantIds()).doesNotContain(before).contains(otherPlant);
        assertThat(data.as(otherPlant, () -> users.count())).isEqualTo(otherBefore);
    }
}
