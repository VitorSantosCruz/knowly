package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** soft-delete-default-filter SPEC requirements 1/2/3, entity: {@code DirectPermissionGrant}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterDirectPermissionGrantIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @Test
    void excludesASoftDeletedDirectPermissionGrantWithNoPerQueryOptIn() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Delete Filter DPG Co"));
        User user = userRepository.saveAndFlush(new User("soft-delete-filter-dpg@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));

        DirectPermissionGrant grant =
                directPermissionGrantRepository.saveAndFlush(
                        new DirectPermissionGrant(membership, Permission.DASHBOARD_VIEW));
        grant.setDeletedAt(Instant.now());
        directPermissionGrantRepository.saveAndFlush(grant);

        var found = testSupportService.findDirectPermissionGrantsByTenantMembership(membership);

        assertThat(found).isEmpty();
    }
}
