package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * tenant-crud REQ-9: {@code deactivateAllByTenant} bulk-flips every currently-active membership of
 * a tenant to {@code active = false}, the same {@code active} flip {@code hardDeleteMember} already
 * applies per-row (alongside {@code deletedAt}).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TenantMembershipRepositoryTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private UserRepository userRepository;

    @Test
    @Transactional
    void deactivateAllByTenantFlipsEveryActiveMembershipToInactive() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Deactivate All Co"));
        User userA = userRepository.saveAndFlush(new User("deactivate-all-a@example.com"));
        User userB = userRepository.saveAndFlush(new User("deactivate-all-b@example.com"));
        TenantMembership membershipA =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(userA, tenant, MembershipRole.MEMBER));
        TenantMembership membershipB =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(userB, tenant, MembershipRole.MEMBER_ADMIN));

        tenantMembershipRepository.deactivateAllByTenant(tenant);

        assertThat(tenantMembershipRepository.findById(membershipA.getId()))
                .hasValueSatisfying(m -> assertThat(m.isActive()).isFalse());
        assertThat(tenantMembershipRepository.findById(membershipB.getId()))
                .hasValueSatisfying(m -> assertThat(m.isActive()).isFalse());
    }

    @Test
    @Transactional
    void deactivateAllByTenantLeavesAnAlreadyInactiveMembershipUntouchedButStillInactive() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Deactivate Already Inactive Co"));
        User user =
                userRepository.saveAndFlush(new User("deactivate-already-inactive@example.com"));
        TenantMembership membership = new TenantMembership(user, tenant, MembershipRole.MEMBER);
        membership.setActive(false);
        TenantMembership saved = tenantMembershipRepository.saveAndFlush(membership);

        tenantMembershipRepository.deactivateAllByTenant(tenant);

        assertThat(tenantMembershipRepository.findById(saved.getId()))
                .hasValueSatisfying(m -> assertThat(m.isActive()).isFalse());
    }

    @Test
    @Transactional
    void deactivateAllByTenantDoesNotTouchAnotherTenantsMemberships() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Deactivate Scope A Co"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Deactivate Scope B Co"));
        User userA = userRepository.saveAndFlush(new User("deactivate-scope-a@example.com"));
        User userB = userRepository.saveAndFlush(new User("deactivate-scope-b@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(userA, tenantA, MembershipRole.MEMBER));
        TenantMembership membershipB =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(userB, tenantB, MembershipRole.MEMBER));

        tenantMembershipRepository.deactivateAllByTenant(tenantA);

        assertThat(tenantMembershipRepository.findById(membershipB.getId()))
                .hasValueSatisfying(m -> assertThat(m.isActive()).isTrue());
    }
}
