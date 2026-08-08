package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** soft-delete-default-filter SPEC requirements 1/2/3, entity: {@code TenantMembership}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterTenantMembershipIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;
    @Autowired private TenantContext tenantContext;

    @AfterEach
    void clearTenantContext() {
        tenantContext.clear();
    }

    @Test
    void excludesASoftDeletedTenantMembershipWithNoPerQueryOptIn() {
        Tenant tenant =
                tenantRepository.saveAndFlush(new Tenant("Soft Delete Filter Membership Co"));
        User liveUser =
                userRepository.saveAndFlush(
                        new User("soft-delete-filter-membership-live@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(liveUser, tenant, MembershipRole.MEMBER));

        User deletedUser =
                userRepository.saveAndFlush(
                        new User("soft-delete-filter-membership-deleted@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(deletedUser, tenant, MembershipRole.MEMBER));
        membership.setDeletedAt(Instant.now());
        tenantMembershipRepository.saveAndFlush(membership);

        // Active tenant set to the same tenant so TenantFilter (the other, independent filter this
        // entity already carries) does not itself hide the row -- isolates the assertion to
        // softDeleteFilter's own effect (SPEC requirement 4: both filters apply independently).
        tenantContext.setActiveTenantId(tenant.getId());

        assertThat(testSupportService.findMembershipByUserAndTenant(liveUser, tenant)).isPresent();
        assertThat(testSupportService.findMembershipByUserAndTenant(deletedUser, tenant)).isEmpty();
    }
}
