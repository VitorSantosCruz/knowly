package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit-style coverage of {@link TenantService#addMember}'s REQ-1/REQ-1a/REQ-13 branching (see
 * specify/features/tenant-membership-acceptance/SPEC.md and PLAN.md's testing strategy).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TenantServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TenantService tenantService;
    @Autowired private TenantContext tenantContext;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
    }

    private TenantMembership adminMembership(String email, Tenant tenant) {
        User admin = userRepository.saveAndFlush(new User(email));
        tenantContext.setActiveTenantId(tenant.getId());

        return tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
    }

    @Test
    void addMemberForABrandNewEmailIsActiveImmediatelyWithNoNotification() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Brand New Co"));
        TenantMembership admin = adminMembership("admin-new@example.com", tenant);

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "brandnew@example.com",
                        MembershipRole.MEMBER);

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.isActive()).isTrue();

        User newUser = userRepository.findByEmailIgnoreCase("brandnew@example.com").orElseThrow();
        assertThat(notificationRepository.findByRecipientAndResolvedFalse(newUser)).isEmpty();
    }

    @Test
    void addMemberForAnExistingUserIsPendingWithAPendingNotification() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Existing Co"));
        TenantMembership admin = adminMembership("admin-existing@example.com", tenant);
        User existing = userRepository.saveAndFlush(new User("existing@example.com"));

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "existing@example.com",
                        MembershipRole.MEMBER);

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membership.isActive()).isFalse();

        var notifications = notificationRepository.findByRecipientAndResolvedFalse(existing);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType())
                .isEqualTo(NotificationType.MEMBERSHIP_INVITATION_PENDING);
        assertThat(notifications.get(0).getTenantMembership().getId())
                .isEqualTo(membership.getId());
    }

    @Test
    void addMemberResetsAPreviouslyDeclinedMembershipToPendingWithAFreshNotification() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Reinvite Co"));
        TenantMembership admin = adminMembership("admin-reinvite@example.com", tenant);
        User declinedUser = userRepository.saveAndFlush(new User("declined@example.com"));
        TenantMembership previouslyDeclined =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(declinedUser, tenant, MembershipRole.MEMBER));
        previouslyDeclined.setStatus(MembershipStatus.DECLINED);
        previouslyDeclined.setActive(false);
        tenantMembershipRepository.saveAndFlush(previouslyDeclined);

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "declined@example.com",
                        MembershipRole.MEMBER);

        assertThat(membership.getId()).isEqualTo(previouslyDeclined.getId());
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membership.isActive()).isFalse();

        var notifications = notificationRepository.findByRecipientAndResolvedFalse(declinedUser);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType())
                .isEqualTo(NotificationType.MEMBERSHIP_INVITATION_PENDING);
    }

    // REQ-13 covers both "previously declined" (above) and "previously removed" rows — a
    // previously-*active*, then soft-removed (via removeMember, which only flips `active`, not
    // `status`) row must also reset to pending on re-invite, not silently stay ACTIVE/inactive.
    @Test
    void addMemberResetsAPreviouslyRemovedMembershipToPendingWithAFreshNotification() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Reinvite After Removal Co"));
        TenantMembership admin = adminMembership("admin-reinvite-removed@example.com", tenant);
        User removedUser = userRepository.saveAndFlush(new User("removed@example.com"));
        TenantMembership previouslyRemoved =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(removedUser, tenant, MembershipRole.MEMBER));
        // Simulate a prior acceptance followed by removeMember: status stays ACTIVE, only
        // `active` is flipped false — removeMember never touches `status`.
        previouslyRemoved.setStatus(MembershipStatus.ACTIVE);
        previouslyRemoved.setActive(false);
        tenantMembershipRepository.saveAndFlush(previouslyRemoved);

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "removed@example.com",
                        MembershipRole.MEMBER);

        assertThat(membership.getId()).isEqualTo(previouslyRemoved.getId());
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membership.isActive()).isFalse();

        var notifications = notificationRepository.findByRecipientAndResolvedFalse(removedUser);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType())
                .isEqualTo(NotificationType.MEMBERSHIP_INVITATION_PENDING);
    }
}
