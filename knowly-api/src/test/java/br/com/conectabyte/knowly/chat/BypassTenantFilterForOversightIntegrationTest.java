package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * TASKS.md item 27/28: a method annotated {@code @BypassTenantFilterForOversight} has the tenant
 * filter disabled for its duration regardless of the caller's active tenant, while a normal
 * {@code @Transactional} method on the same entity stays correctly scoped -- proving the extension
 * is additive to {@link br.com.conectabyte.knowly.tenancy.TenantFilterAspect}'s existing behavior,
 * not a replacement of it. Both reads go through {@link ChatOversightConversationLoader}'s real
 * Spring proxy, so the {@code @Transactional}/{@code @Around} advice actually fires.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class BypassTenantFilterForOversightIntegrationTest {

    @Autowired private ChatOversightConversationLoader oversightConversationLoader;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantContext tenantContext;

    @AfterEach
    void clearTenantContext() {
        tenantContext.clear();
    }

    @Test
    void bypassAnnotatedMethodSeesAStaffOnlyConversationThatTheNormalPathCannot() {
        Tenant activeTenant = tenantRepository.saveAndFlush(new Tenant("Filter Test Tenant"));

        // Insert as staff-with-no-active-tenant so the existing aspect branch disables the filter
        // for the write, matching how a staff-only (tenant_id = NULL) conversation would really be
        // created.
        tenantContext.setStaff(true);
        tenantContext.setStaffAdmin(true);
        ChatConversation staffOnly =
                oversightConversationLoader.save(
                        new ChatConversation(
                                ChatConversationKind.PEER_GROUP, null, "Staff Only", null));

        // Now simulate a plain member with an active tenant selected -- the normal filtered path
        // must not see the NULL-tenant row.
        tenantContext.setStaff(false);
        tenantContext.setStaffAdmin(false);
        tenantContext.setActiveTenantId(activeTenant.getId());

        var normalRead = oversightConversationLoader.loadRespectingTenantFilter(staffOnly.getId());
        assertThat(normalRead).isEmpty();

        var bypassedRead = oversightConversationLoader.loadIgnoringTenantFilter(staffOnly.getId());
        assertThat(bypassedRead).isPresent();
    }
}
