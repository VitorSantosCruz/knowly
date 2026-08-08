package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.AccessGroup;
import br.com.conectabyte.knowly.tenancy.AccessGroupRepository;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.UserAccessGroup;
import br.com.conectabyte.knowly.tenancy.UserAccessGroupRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** soft-delete-default-filter SPEC requirements 1/2/3, entity: {@code UserAccessGroup}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterUserAccessGroupIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private UserAccessGroupRepository userAccessGroupRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;
    @Autowired private TenantContext tenantContext;

    @AfterEach
    void clearTenantContext() {
        tenantContext.clear();
    }

    @Test
    void excludesASoftDeletedUserAccessGroupWithNoPerQueryOptIn() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Delete Filter UAG Co"));
        tenantContext.setActiveTenantId(tenant.getId());
        User user = userRepository.saveAndFlush(new User("soft-delete-filter-uag@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));
        AccessGroup accessGroup =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));

        UserAccessGroup assignment =
                userAccessGroupRepository.saveAndFlush(
                        new UserAccessGroup(membership, accessGroup));
        assignment.setDeletedAt(Instant.now());
        userAccessGroupRepository.saveAndFlush(assignment);

        var found = testSupportService.findUserAccessGroupsByTenantMembership(membership);

        assertThat(found).isEmpty();
    }
}
