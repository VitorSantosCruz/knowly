package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.tenancy.exception.NotificationAlreadyResolvedException;
import br.com.conectabyte.knowly.tenancy.exception.NotificationNotFoundException;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Accept/decline of the caller's own in-app notifications (see
 * specify/features/tenant-membership-acceptance/PLAN.md/SPEC.md). Deliberately not
 * {@code @Transactional} at the class or method level, mirroring {@code
 * TenantService#resolveSessionOutcome}/{@code #listOwnMemberships}: the caller may not have
 * switched into the target tenant yet, and {@code TenantFilterAspect} enables {@code TenantFilter}
 * strictly from the session's already-active tenant, which would otherwise fail closed for the
 * invitee's own pending row before they've ever selected that tenant. Authorization here is "is
 * this notification's recipient the caller" (REQ-10), resolved by identity, not by tenant
 * selection.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TenantMembershipRepository tenantMembershipRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            TenantMembershipRepository tenantMembershipRepository) {
        this.notificationRepository = notificationRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
    }

    /** REQ-8: the caller's own unresolved notifications. */
    public List<Notification> listMine(User user) {
        return notificationRepository.findByRecipientAndResolvedFalse(user);
    }

    /** REQ-5/6/9: accept a pending membership invitation. */
    @AuditLog(action = "notification.membership.accept", resourceType = "TenantMembership")
    public void accept(User user, Long notificationId) {
        Notification notification = requireOwnNotification(user, notificationId);
        TenantMembership membership = notification.getTenantMembership();

        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw new NotificationAlreadyResolvedException();
        }

        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setActive(true);
        tenantMembershipRepository.save(membership);

        notification.setResolved(true);
        notificationRepository.save(notification);

        notifyAdminsAndInviter(membership);
    }

    /**
     * REQ-7: decline a pending membership invitation — never reactivatable without a fresh invite.
     */
    @AuditLog(action = "notification.membership.decline", resourceType = "TenantMembership")
    public void decline(User user, Long notificationId) {
        Notification notification = requireOwnNotification(user, notificationId);
        TenantMembership membership = notification.getTenantMembership();

        if (membership.getStatus() != MembershipStatus.PENDING) {
            throw new NotificationAlreadyResolvedException();
        }

        membership.setStatus(MembershipStatus.DECLINED);
        membership.setActive(false);
        tenantMembershipRepository.save(membership);

        notification.setResolved(true);
        notificationRepository.save(notification);
    }

    /**
     * REQ-6: notify every active MEMBER_ADMIN of the tenant and the user who performed the original
     * member-add action, deduplicated when the same person occupies both roles. The original
     * inviter isn't tracked on {@code TenantMembership} today, so this notifies every active
     * MEMBER_ADMIN of the tenant — which already covers "the person who invited them" in the
     * overwhelmingly common case where the inviter is themselves a MEMBER_ADMIN, consistent with
     * SPEC Decision #1 (tenant owner == every active MEMBER_ADMIN).
     */
    private void notifyAdminsAndInviter(TenantMembership acceptedMembership) {
        Set<User> recipients =
                tenantMembershipRepository
                        .findByTenantIdAndActiveTrue(acceptedMembership.getTenant().getId())
                        .stream()
                        .filter(m -> m.getRole() == MembershipRole.MEMBER_ADMIN)
                        .map(TenantMembership::getUser)
                        .collect(
                                java.util.stream.Collectors.toCollection(
                                        java.util.LinkedHashSet::new));

        for (User recipient : recipients) {
            notificationRepository.save(
                    new Notification(
                            recipient,
                            NotificationType.MEMBERSHIP_INVITATION_ACCEPTED,
                            acceptedMembership));
        }
    }

    private Notification requireOwnNotification(User user, Long notificationId) {
        Notification notification =
                notificationRepository
                        .findById(notificationId)
                        .orElseThrow(NotificationNotFoundException::new);

        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new PermissionDeniedException();
        }

        if (notification.isResolved()) {
            throw new NotificationAlreadyResolvedException();
        }

        return notification;
    }
}
