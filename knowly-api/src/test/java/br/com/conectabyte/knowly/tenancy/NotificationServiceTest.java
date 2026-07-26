package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.exception.NotificationAlreadyResolvedException;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers {@link NotificationService}'s listMine/accept/decline per
 * specify/features/tenant-membership-acceptance/SPEC.md REQ-5..REQ-11.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;

    private TenantMembership pendingMembership(String email, Tenant tenant) {
        User user = userRepository.saveAndFlush(new User(email));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));
        membership.setStatus(MembershipStatus.PENDING);
        membership.setActive(false);
        return tenantMembershipRepository.saveAndFlush(membership);
    }

    private Notification pendingNotificationFor(TenantMembership membership) {
        return notificationRepository.saveAndFlush(
                new Notification(
                        membership.getUser(),
                        NotificationType.MEMBERSHIP_INVITATION_PENDING,
                        membership));
    }

    @Test
    void listMineReturnsOnlyTheCallersOwnUnresolvedNotifications() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("List Co"));
        TenantMembership mine = pendingMembership("listmine@example.com", tenant);
        TenantMembership someoneElses = pendingMembership("someoneelse@example.com", tenant);
        Notification myNotification = pendingNotificationFor(mine);
        pendingNotificationFor(someoneElses);

        var results = notificationService.listMine(mine.getUser());

        assertThat(results).extracting(Notification::getId).containsExactly(myNotification.getId());
    }

    @Test
    void acceptingActivatesTheMembershipResolvesTheNotificationAndNotifiesMemberAdmins() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Accept Co"));
        User admin = userRepository.saveAndFlush(new User("admin-accept@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        TenantMembership invitee = pendingMembership("invitee-accept@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);

        notificationService.accept(invitee.getUser(), notification.getId());

        TenantMembership reloaded =
                tenantMembershipRepository.findById(invitee.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(reloaded.isActive()).isTrue();

        Notification reloadedNotification =
                notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloadedNotification.isResolved()).isTrue();

        var adminNotifications = notificationRepository.findByRecipientAndResolvedFalse(admin);
        assertThat(adminNotifications).hasSize(1);
        assertThat(adminNotifications.get(0).getType())
                .isEqualTo(NotificationType.MEMBERSHIP_INVITATION_ACCEPTED);
        assertThat(adminNotifications.get(0).getTenantMembership().getId())
                .isEqualTo(invitee.getId());
    }

    @Test
    void acceptingDeduplicatesWhenTheSamePersonIsTheOnlyMemberAdmin() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Dedup Co"));
        User admin = userRepository.saveAndFlush(new User("soleadmin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        TenantMembership invitee = pendingMembership("invitee-dedup@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);

        notificationService.accept(invitee.getUser(), notification.getId());

        assertThat(notificationRepository.findByRecipientAndResolvedFalse(admin)).hasSize(1);
    }

    @Test
    void decliningMarksTheMembershipDeclinedAndResolvesTheNotificationWithNoNewNotification() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Decline Co"));
        User admin = userRepository.saveAndFlush(new User("admin-decline@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        TenantMembership invitee = pendingMembership("invitee-decline@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);

        notificationService.decline(invitee.getUser(), notification.getId());

        TenantMembership reloaded =
                tenantMembershipRepository.findById(invitee.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MembershipStatus.DECLINED);
        assertThat(reloaded.isActive()).isFalse();

        Notification reloadedNotification =
                notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloadedNotification.isResolved()).isTrue();

        assertThat(notificationRepository.findByRecipientAndResolvedFalse(admin)).isEmpty();
    }

    @Test
    void acceptingOrDecliningANotificationNotAddressedToTheCallerIsRejected() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Wrong Recipient Co"));
        TenantMembership invitee = pendingMembership("realrecipient@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);
        User impostor = userRepository.saveAndFlush(new User("impostor@example.com"));

        assertThatThrownBy(() -> notificationService.accept(impostor, notification.getId()))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> notificationService.decline(impostor, notification.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    /**
     * Regression test pinning a known, documented limitation (see NotificationService's
     * notifyAdminsAndInviter javadoc and SPEC.md Decision #1): REQ-6 asks to notify "the original
     * inviter," but {@code TenantMembership} has no {@code invitedBy} column, so this project
     * notifies every active MEMBER_ADMIN of the tenant instead. When the actual inviter is a staff
     * actor with no active membership row in the tenant (the staff bypass never creates one), they
     * are a real "inviter" who is NOT notified on acceptance — this test exists so that gap is
     * visible and asserted, not silently unverified.
     */
    @Test
    void staffInviterWithNoActiveMembershipInTenantIsNotNotifiedOnAcceptance() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Staff Inviter Gap Co"));
        User staffInviter = userRepository.saveAndFlush(new User("staff-inviter@example.com"));
        staffInviter.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffInviter);
        // Deliberately no TenantMembership row for staffInviter in this tenant — staff bypass
        // access is GlobalRole-based, not membership-based, per role-model-refinement.
        TenantMembership invitee = pendingMembership("staff-invited@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);

        notificationService.accept(invitee.getUser(), notification.getId());

        assertThat(notificationRepository.findByRecipientAndResolvedFalse(staffInviter)).isEmpty();
    }

    /**
     * REQ-11's "membership no longer pending" branch is distinct from "notification already
     * resolved" — this exercises {@code accept}/{@code decline}'s own {@code membership.getStatus()
     * != PENDING} guard directly, by leaving the notification unresolved but advancing the
     * underlying membership out of PENDING through another path first (e.g. a second,
     * still-unresolved notification referencing an already-actioned membership).
     */
    @Test
    void
            acceptingWhenTheReferencedMembershipIsNoLongerPendingIsRejectedEvenIfTheNotificationItselfIsUnresolved() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Stale Membership Co"));
        TenantMembership invitee = pendingMembership("stale-membership@example.com", tenant);
        Notification firstNotification = pendingNotificationFor(invitee);
        Notification secondNotification = pendingNotificationFor(invitee);

        notificationService.accept(invitee.getUser(), firstNotification.getId());

        assertThatThrownBy(
                        () ->
                                notificationService.accept(
                                        invitee.getUser(), secondNotification.getId()))
                .isInstanceOf(NotificationAlreadyResolvedException.class);
        assertThatThrownBy(
                        () ->
                                notificationService.decline(
                                        invitee.getUser(), secondNotification.getId()))
                .isInstanceOf(NotificationAlreadyResolvedException.class);
    }

    @Test
    void acceptingOrDecliningAnAlreadyResolvedNotificationIsRejected() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Already Resolved Co"));
        TenantMembership invitee = pendingMembership("alreadyresolved@example.com", tenant);
        Notification notification = pendingNotificationFor(invitee);

        notificationService.accept(invitee.getUser(), notification.getId());

        assertThatThrownBy(
                        () -> notificationService.accept(invitee.getUser(), notification.getId()))
                .isInstanceOf(NotificationAlreadyResolvedException.class);
        assertThatThrownBy(
                        () -> notificationService.decline(invitee.getUser(), notification.getId()))
                .isInstanceOf(NotificationAlreadyResolvedException.class);
    }
}
