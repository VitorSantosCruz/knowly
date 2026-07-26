package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.ProfileFieldsDto;
import br.com.conectabyte.knowly.identity.exception.PendingProfileEditRequestExistsException;
import br.com.conectabyte.knowly.identity.exception.ProfileEditRequestAlreadyResolvedException;
import br.com.conectabyte.knowly.identity.exception.ProfileEditRequestNotFoundException;
import br.com.conectabyte.knowly.identity.exception.ProfileFieldConflictException;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Notification;
import br.com.conectabyte.knowly.tenancy.NotificationRepository;
import br.com.conectabyte.knowly.tenancy.NotificationType;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.PermissionService;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Submit/approve/reject of a user's own self-requested profile-edit request (REQ-15..21), per
 * specify/features/identity-profile-model/PLAN.md. Deliberately not {@code @Transactional} on the
 * class or read-heavy methods, same rationale as {@code NotificationService}/{@link
 * UserProfileService}: the recipient-enumeration/right-check logic here must see every one of the
 * requester's/caller's memberships across every tenant, not just the caller's currently-active one.
 */
@Service
public class ProfileEditRequestService {

    private final ProfileEditRequestRepository profileEditRequestRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final PermissionService permissionService;
    private final GlobalPermissionService globalPermissionService;
    private final UserProfileService userProfileService;

    public ProfileEditRequestService(
            ProfileEditRequestRepository profileEditRequestRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            TenantMembershipRepository tenantMembershipRepository,
            PermissionService permissionService,
            GlobalPermissionService globalPermissionService,
            UserProfileService userProfileService) {
        this.profileEditRequestRepository = profileEditRequestRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.permissionService = permissionService;
        this.globalPermissionService = globalPermissionService;
        this.userProfileService = userProfileService;
    }

    /**
     * GET .../profile-edit-requests: pending requests the caller holds the applicable right over.
     */
    public java.util.List<ProfileEditRequest> listPendingForApprover(User caller) {
        return profileEditRequestRepository.findByStatus(ProfileEditRequestStatus.PENDING).stream()
                .filter(
                        request ->
                                userProfileService.hasDirectEditRight(
                                        caller, request.getRequester()))
                .toList();
    }

    /** REQ-15/16/20: create a pending request and notify every applicable edit-right holder. */
    @AuditLog(action = "identity.profile.edit_request.submit", resourceType = "ProfileEditRequest")
    public ProfileEditRequest submitEditRequest(User requester, ProfileFieldsDto proposed) {
        profileEditRequestRepository
                .findByRequesterAndStatus(requester, ProfileEditRequestStatus.PENDING)
                .ifPresent(
                        existing -> {
                            throw new PendingProfileEditRequestExistsException();
                        });

        ProfileEditRequest request = new ProfileEditRequest(requester);
        request.setProposedFullName(proposed.fullName());
        request.setProposedAddress(proposed.address());
        request.setProposedRg(proposed.rg());
        request.setProposedCpf(proposed.cpf());
        request.setProposedPhone(proposed.phone());
        request.setStatus(ProfileEditRequestStatus.PENDING);
        request = profileEditRequestRepository.save(request);

        for (User recipient : applicableEditRightHolders(requester)) {
            notificationRepository.save(
                    new Notification(
                            recipient, NotificationType.PROFILE_EDIT_REQUEST_PENDING, request));
        }

        return request;
    }

    /** REQ-17/21: apply the proposed fields and resolve as approved. */
    @AuditLog(
            action = "identity.profile.edit_request.approve",
            resourceType = "ProfileEditRequest",
            resourceIdExpression = "#requestId")
    public ProfileEditRequest approveEditRequest(User caller, Long requestId) {
        ProfileEditRequest request = requirePendingRequest(caller, requestId);

        ProfileFieldsDto proposed =
                new ProfileFieldsDto(
                        request.getProposedFullName(),
                        request.getProposedAddress(),
                        request.getProposedRg(),
                        request.getProposedCpf(),
                        request.getProposedPhone());

        try {
            userProfileService.applyFields(request.getRequester(), proposed);
            userRepository.save(request.getRequester());
        } catch (DataIntegrityViolationException e) {
            throw new ProfileFieldConflictException();
        }

        request.setStatus(ProfileEditRequestStatus.APPROVED);
        request.setResolvedBy(caller);
        request.setResolvedAt(Instant.now());

        return profileEditRequestRepository.save(request);
    }

    /** REQ-18: discard the proposed values and resolve as rejected. */
    @AuditLog(
            action = "identity.profile.edit_request.reject",
            resourceType = "ProfileEditRequest",
            resourceIdExpression = "#requestId")
    public ProfileEditRequest rejectEditRequest(User caller, Long requestId) {
        ProfileEditRequest request = requirePendingRequest(caller, requestId);

        request.setStatus(ProfileEditRequestStatus.REJECTED);
        request.setResolvedBy(caller);
        request.setResolvedAt(Instant.now());

        return profileEditRequestRepository.save(request);
    }

    /** REQ-19: only a caller holding the applicable edit right over the requester may resolve. */
    private ProfileEditRequest requirePendingRequest(User caller, Long requestId) {
        ProfileEditRequest request =
                profileEditRequestRepository
                        .findById(requestId)
                        .orElseThrow(ProfileEditRequestNotFoundException::new);

        if (request.getStatus() != ProfileEditRequestStatus.PENDING) {
            throw new ProfileEditRequestAlreadyResolvedException();
        }

        if (!userProfileService.hasDirectEditRight(caller, request.getRequester())) {
            throw new PermissionDeniedException();
        }

        return request;
    }

    /**
     * REQ-16: every MEMBER_ADMIN of a tenant the requester belongs to, every tenant-scoped
     * PROFILE_EDIT holder in those tenants, every STAFF_ADMIN, and every global-scoped PROFILE_EDIT
     * holder -- deduplicated by recipient, excluding the requester themself.
     */
    private Set<User> applicableEditRightHolders(User requester) {
        Set<User> recipients = new LinkedHashSet<>();

        for (TenantMembership requesterMembership :
                tenantMembershipRepository.findByUserAndActiveTrue(requester)) {
            Long tenantId = requesterMembership.getTenant().getId();

            for (TenantMembership membership :
                    tenantMembershipRepository.findByTenantIdAndActiveTrue(tenantId)) {
                if (membership.getRole() == MembershipRole.MEMBER_ADMIN
                        || permissionService.hasPermission(membership, Permission.PROFILE_EDIT)) {
                    recipients.add(membership.getUser());
                }
            }
        }

        for (User staff :
                userRepository.findByGlobalRoleIn(
                        List.of(GlobalRole.STAFF_ADMIN, GlobalRole.STAFF))) {
            if (staff.getGlobalRole() == GlobalRole.STAFF_ADMIN
                    || globalPermissionService.hasPermission(
                            staff, GlobalPermission.PROFILE_EDIT)) {
                recipients.add(staff);
            }
        }

        recipients.remove(requester);

        return recipients;
    }
}
