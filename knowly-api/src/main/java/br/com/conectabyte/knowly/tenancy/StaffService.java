package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.RequiresGlobalPermission;
import br.com.conectabyte.knowly.auth.MailService;
import br.com.conectabyte.knowly.auth.OneTimePasswordService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.deletion.DeletionConfirmationTokenService;
import br.com.conectabyte.knowly.deletion.exception.DeletionConfirmationInvalidException;
import br.com.conectabyte.knowly.identity.UserProfile;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import br.com.conectabyte.knowly.identity.UserProfileService;
import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.identity.exception.UserNotFoundException;
import br.com.conectabyte.knowly.tenancy.dto.AuditEventDto;
import br.com.conectabyte.knowly.tenancy.dto.GlobalAccessGroupDto;
import br.com.conectabyte.knowly.tenancy.dto.StaffUserDetailDto;
import br.com.conectabyte.knowly.tenancy.dto.StaffUserSummaryDto;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import br.com.conectabyte.knowly.tenancy.exception.StaffUserAlreadyExistsException;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages global permissions/access-groups for {@code STAFF} users — the global-scope equivalent of
 * {@link TenantService}'s tenant-scoped permission/access-group management, restricted to {@code
 * STAFF_ADMIN} (or a {@code STAFF} user holding {@link GlobalPermission#STAFF_PERMISSION_MANAGE}),
 * per specify/features/staff-rbac-split/SPEC.md REQ-7.
 */
@Service
public class StaffService {

    private final UserRepository userRepository;
    private final DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    private final GlobalAccessGroupRepository globalAccessGroupRepository;
    private final GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository;
    private final UserGlobalAccessGroupRepository userGlobalAccessGroupRepository;
    private final GlobalPermissionService globalPermissionService;
    private final OneTimePasswordService oneTimePasswordService;
    private final MailService mailService;
    private final AuditEventRepository auditEventRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileService userProfileService;
    private final DeletionConfirmationTokenService deletionConfirmationTokenService;

    private static final String PERMISSION_RESOURCE_TYPE = "staff-permission";
    private static final String ACCESS_GROUP_RESOURCE_TYPE = "staff-access-group";

    public StaffService(
            UserRepository userRepository,
            DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository,
            GlobalAccessGroupRepository globalAccessGroupRepository,
            GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository,
            UserGlobalAccessGroupRepository userGlobalAccessGroupRepository,
            GlobalPermissionService globalPermissionService,
            OneTimePasswordService oneTimePasswordService,
            MailService mailService,
            AuditEventRepository auditEventRepository,
            UserProfileRepository userProfileRepository,
            UserProfileService userProfileService,
            DeletionConfirmationTokenService deletionConfirmationTokenService) {
        this.userRepository = userRepository;
        this.directGlobalPermissionGrantRepository = directGlobalPermissionGrantRepository;
        this.globalAccessGroupRepository = globalAccessGroupRepository;
        this.globalAccessGroupPermissionRepository = globalAccessGroupPermissionRepository;
        this.userGlobalAccessGroupRepository = userGlobalAccessGroupRepository;
        this.globalPermissionService = globalPermissionService;
        this.oneTimePasswordService = oneTimePasswordService;
        this.userProfileRepository = userProfileRepository;
        this.userProfileService = userProfileService;
        this.mailService = mailService;
        this.auditEventRepository = auditEventRepository;
        this.deletionConfirmationTokenService = deletionConfirmationTokenService;
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_USER_CREATE)
    @AuditLog(action = "staff.user.create", resourceType = "User")
    public User createStaffUser(String email, MandatoryProfileFieldsDto profile) {
        enforceStaffCeiling(GlobalRole.STAFF);

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new StaffUserAlreadyExistsException();
        }

        User user = new User(email);
        user.setGlobalRole(GlobalRole.STAFF);
        user = userRepository.save(user);
        userProfileRepository.save(new UserProfile(user));
        userProfileService.applyMandatoryProfile(user, profile);

        String oneTimePassword = oneTimePasswordService.generateFor(user);
        mailService.sendNewOneTimePassword(user.getEmail(), oneTimePassword);

        return user;
    }

    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(
            action = "staff.user.detail.view",
            resourceType = "User",
            resourceIdExpression = "#userId")
    public StaffUserDetailDto getStaffUserDetail(Long userId) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());

        List<GlobalPermission> direct =
                directGlobalPermissionGrantRepository.findByUser(user).stream()
                        .map(DirectGlobalPermissionGrant::getPermission)
                        .toList();
        List<GlobalAccessGroupDto> groups =
                userGlobalAccessGroupRepository.findByUser(user).stream()
                        .map(UserGlobalAccessGroup::getGlobalAccessGroup)
                        .map(GlobalAccessGroupDto::from)
                        .toList();
        List<GlobalPermission> effective =
                globalPermissionService.effectivePermissions(user).stream().toList();

        return new StaffUserDetailDto(user.getId(), user.getEmail(), direct, groups, effective);
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(action = "staff.permission.grant", resourceType = "DirectGlobalPermissionGrant")
    public void grantPermission(Long userId, GlobalPermission permission) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());

        directGlobalPermissionGrantRepository
                .findByUserAndPermission(user, permission)
                .orElseGet(
                        () ->
                                directGlobalPermissionGrantRepository.save(
                                        new DirectGlobalPermissionGrant(user, permission)));
    }

    /** REQ-26: generation endpoint reuses the exact same guard as {@link #revokePermission}. */
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    public String generatePermissionRevocationDeletionConfirmationToken(
            Long userId, GlobalPermission permission, String acceptLanguageHeaderValue) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());

        return deletionConfirmationTokenService.generate(
                PERMISSION_RESOURCE_TYPE,
                userId + ":" + permission,
                currentActor(),
                acceptLanguageHeaderValue);
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(action = "staff.permission.revoke", resourceType = "DirectGlobalPermissionGrant")
    public void revokePermission(Long userId, GlobalPermission permission, String word) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());

        if (!deletionConfirmationTokenService.validateAndConsume(
                PERMISSION_RESOURCE_TYPE, userId + ":" + permission, currentActor(), word)) {
            throw new DeletionConfirmationInvalidException();
        }

        directGlobalPermissionGrantRepository
                .findByUserAndPermission(user, permission)
                .ifPresent(directGlobalPermissionGrantRepository::delete);
    }

    /**
     * Lists every {@code STAFF}/{@code STAFF_ADMIN} user (id/email/globalRole), optionally filtered
     * by an email substring. Deliberately does not call {@link #enforceStaffCeiling(GlobalRole)} —
     * per specify/features/staff-user-listing/SPEC.md REQ-6, merely seeing that a staff account
     * exists is a distinct capability from managing it, and every management method independently
     * re-checks the ceiling.
     */
    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.STAFF_USER_VIEW)
    public List<StaffUserSummaryDto> listStaffUsers(String emailFilter) {
        List<GlobalRole> staffRoles = List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN);
        List<User> users =
                (emailFilter == null || emailFilter.isBlank())
                        ? userRepository.findByGlobalRoleIn(staffRoles)
                        : userRepository.findByGlobalRoleInAndEmailContainingIgnoreCase(
                                staffRoles, emailFilter);

        return users.stream().map(StaffUserSummaryDto::from).toList();
    }

    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    public List<GlobalAccessGroupDto> listAccessGroups() {
        return globalAccessGroupRepository.findAll().stream()
                .map(GlobalAccessGroupDto::from)
                .toList();
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(action = "staff.access_group.create", resourceType = "GlobalAccessGroup")
    public GlobalAccessGroup createAccessGroup(String name) {
        return globalAccessGroupRepository.save(new GlobalAccessGroup(name));
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(
            action = "staff.access_group.grant_permission",
            resourceType = "GlobalAccessGroupPermission")
    public void grantAccessGroupPermission(Long accessGroupId, GlobalPermission permission) {
        GlobalAccessGroup accessGroup = requireAccessGroup(accessGroupId);

        globalAccessGroupPermissionRepository
                .findByGlobalAccessGroupAndPermission(accessGroup, permission)
                .orElseGet(
                        () ->
                                globalAccessGroupPermissionRepository.save(
                                        new GlobalAccessGroupPermission(accessGroup, permission)));
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(action = "staff.member.access_group.assign", resourceType = "UserGlobalAccessGroup")
    public void assignAccessGroup(Long userId, Long accessGroupId) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());
        GlobalAccessGroup accessGroup = requireAccessGroup(accessGroupId);

        userGlobalAccessGroupRepository
                .findByUserAndGlobalAccessGroup(user, accessGroup)
                .orElseGet(
                        () ->
                                userGlobalAccessGroupRepository.save(
                                        new UserGlobalAccessGroup(user, accessGroup)));
    }

    /** REQ-29: generation endpoint reuses the exact same guard as {@link #unassignAccessGroup}. */
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    public String generateAccessGroupUnassignmentDeletionConfirmationToken(
            Long userId, Long accessGroupId, String acceptLanguageHeaderValue) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());

        return deletionConfirmationTokenService.generate(
                ACCESS_GROUP_RESOURCE_TYPE,
                userId + ":" + accessGroupId,
                currentActor(),
                acceptLanguageHeaderValue);
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(action = "staff.member.access_group.unassign", resourceType = "UserGlobalAccessGroup")
    public void unassignAccessGroup(Long userId, Long accessGroupId, String word) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());

        if (!deletionConfirmationTokenService.validateAndConsume(
                ACCESS_GROUP_RESOURCE_TYPE, userId + ":" + accessGroupId, currentActor(), word)) {
            throw new DeletionConfirmationInvalidException();
        }

        GlobalAccessGroup accessGroup = requireAccessGroup(accessGroupId);

        userGlobalAccessGroupRepository
                .findByUserAndGlobalAccessGroup(user, accessGroup)
                .ifPresent(userGlobalAccessGroupRepository::delete);
    }

    /**
     * specify/features/staff-audit-trail-view/SPEC.md REQ-1..REQ-9: returns the target user's full
     * audit history, including rows from every tenant they've ever acted in — {@code AuditEvent} is
     * not a {@code TenantAwareEntity} and has no {@code @Filter}, so no special-case plumbing is
     * needed to bypass tenant scoping. This is a deliberate, confirmed exception (see SPEC's
     * "Context and motivation"/"Tier 3 flag"). Deliberately does not call {@link
     * #enforceStaffCeiling(GlobalRole)} — per REQ-9, viewing a STAFF/STAFF_ADMIN target's history
     * grants no ability to act on the account, mirroring {@link #listStaffUsers(String)}.
     */
    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.AUDIT_TRAIL_VIEW)
    @AuditLog(
            action = "staff.audit_trail.view",
            resourceType = "User",
            resourceIdExpression = "#userId")
    public List<AuditEventDto> getAuditTrail(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }

        return auditEventRepository.findTop500ByActorUserIdOrderByOccurredAtDesc(userId).stream()
                .map(AuditEventDto::from)
                .toList();
    }

    private void enforceStaffCeiling(GlobalRole targetGlobalRole) {
        User actor = currentActor();

        if (actor.getGlobalRole() == GlobalRole.STAFF
                && (targetGlobalRole == GlobalRole.STAFF
                        || targetGlobalRole == GlobalRole.STAFF_ADMIN)) {
            throw new PermissionDeniedException();
        }
    }

    private User currentActor() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(PermissionDeniedException::new);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(TenantAccessDeniedException::new);
    }

    private GlobalAccessGroup requireAccessGroup(Long accessGroupId) {
        return globalAccessGroupRepository
                .findById(accessGroupId)
                .orElseThrow(TenantAccessDeniedException::new);
    }
}
