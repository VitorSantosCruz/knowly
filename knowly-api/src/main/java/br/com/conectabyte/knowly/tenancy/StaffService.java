package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditEventWriter;
import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.AuditOutcome;
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
import br.com.conectabyte.knowly.tenancy.exception.LastAdminRemainingException;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import br.com.conectabyte.knowly.tenancy.exception.StaffUserAlreadyExistsException;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final AuditEventWriter auditEventWriter;

    private static final String PERMISSION_RESOURCE_TYPE = "staff-permission";
    private static final String ACCESS_GROUP_RESOURCE_TYPE = "staff-access-group";
    private static final String DELETION_RESOURCE_TYPE = "staff-user";
    private static final String BATCH_RESOURCE_TYPE = "staff-permission-batch";

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
            DeletionConfirmationTokenService deletionConfirmationTokenService,
            AuditEventWriter auditEventWriter) {
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
        this.auditEventWriter = auditEventWriter;
    }

    /**
     * REQ-1/REQ-4 (user-role-selection-at-creation): {@code role} is optional -- {@code null} or
     * {@code STAFF} defaults to today's {@code STAFF} behavior. REQ-2/REQ-3: {@code STAFF_ADMIN} is
     * only honored when the caller is themselves a {@code STAFF_ADMIN} ({@link
     * #requireCallerIsStaffAdmin()}) -- no permission-grant substitution, unlike the {@code
     * STAFF_USER_CREATE} gate below which governs the base "can this caller create a staff user at
     * all" question. REQ-5: no floor/ceiling check applies to creation.
     */
    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_USER_CREATE)
    @AuditLog(action = "staff.user.create", resourceType = "User", metadataExpression = "#role")
    public User createStaffUser(String email, GlobalRole role, MandatoryProfileFieldsDto profile) {
        GlobalRole resolvedRole = role == null ? GlobalRole.STAFF : role;

        if (resolvedRole == GlobalRole.STAFF_ADMIN) {
            requireCallerIsStaffAdmin();
        } else {
            enforceStaffCeiling(GlobalRole.STAFF);
        }

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new StaffUserAlreadyExistsException();
        }

        User user = new User(email);
        user.setGlobalRole(resolvedRole);
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
        boolean isLastAdminOfType =
                user.getGlobalRole() == GlobalRole.STAFF_ADMIN
                        && userRepository.countByGlobalRoleIn(List.of(GlobalRole.STAFF_ADMIN)) == 1;

        return new StaffUserDetailDto(
                user.getId(),
                user.getEmail(),
                user.getGlobalRole(),
                direct,
                groups,
                effective,
                isLastAdminOfType);
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(action = "staff.permission.grant", resourceType = "DirectGlobalPermissionGrant")
    public void grantPermission(Long userId, GlobalPermission permission) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());
        rejectAdminTarget(user);

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
        rejectAdminTarget(user);
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

    /**
     * REQ-1/REQ-21: {@code STAFF_ADMIN} -> {@code STAFF}, rejected (409) if the target is the last
     * {@code STAFF_ADMIN} (locked count, closes the TOCTOU window) or is the caller themselves. A
     * target who isn't currently {@code STAFF_ADMIN} is a no-op.
     */
    @Transactional
    @AuditLog(action = "staff.user.demote", resourceType = "User", resourceIdExpression = "#userId")
    public void demoteStaffUser(Long userId) {
        requireCallerIsStaffAdmin();
        User user = requireUser(userId);
        requireNotSelfTarget(userId);

        if (user.getGlobalRole() != GlobalRole.STAFF_ADMIN) {
            return;
        }

        requireNotLastStaffAdmin(userId);
        user.setGlobalRole(GlobalRole.STAFF);
        userRepository.save(user);
    }

    /**
     * REQ-24/REQ-27/REQ-28: {@code STAFF} -> {@code STAFF_ADMIN}, no floor/ceiling check (REQ-26),
     * rejected only if the caller isn't {@code STAFF_ADMIN} or targets themselves.
     */
    @Transactional
    @AuditLog(
            action = "staff.user.promote",
            resourceType = "User",
            resourceIdExpression = "#userId")
    public void promoteStaffUser(Long userId) {
        requireCallerIsStaffAdmin();
        User user = requireUser(userId);
        requireNotSelfTarget(userId);

        user.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.save(user);
    }

    /** REQ-9: generation endpoint reuses the exact same guard as {@link #deleteStaffUser}. */
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    public String generateStaffUserDeletionConfirmationToken(
            Long userId, String acceptLanguageHeaderValue) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());

        return deletionConfirmationTokenService.generate(
                DELETION_RESOURCE_TYPE,
                userId.toString(),
                currentActor(),
                acceptLanguageHeaderValue);
    }

    /**
     * REQ-7/8/10/11: hard delete, requires a valid deletion-confirmation token, rejects self-target
     * and the last {@code STAFF_ADMIN} (locked count); never blocked for a plain {@code STAFF}
     * target. Dependent {@code DirectGlobalPermissionGrant}/{@code UserGlobalAccessGroup} rows are
     * removed by the existing {@code ON DELETE CASCADE} FK (see PLAN.md's "Data schema").
     */
    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(action = "staff.user.delete", resourceType = "User", resourceIdExpression = "#userId")
    public void deleteStaffUser(Long userId, String word) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());
        requireNotSelfTarget(userId);

        if (!deletionConfirmationTokenService.validateAndConsume(
                DELETION_RESOURCE_TYPE, userId.toString(), currentActor(), word)) {
            throw new DeletionConfirmationInvalidException();
        }

        if (user.getGlobalRole() == GlobalRole.STAFF_ADMIN) {
            requireNotLastStaffAdmin(userId);
        }

        userRepository.delete(user);
    }

    /**
     * REQ-16: generation endpoint reuses the exact same guard as {@link
     * #batchUpdatePermissions(Long, Set, String)}.
     */
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    public String generateBatchPermissionUpdateDeletionConfirmationToken(
            Long userId, String acceptLanguageHeaderValue) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());

        return deletionConfirmationTokenService.generate(
                BATCH_RESOURCE_TYPE, userId.toString(), currentActor(), acceptLanguageHeaderValue);
    }

    /**
     * REQ-12/13/14/15/16: full-set replacement of directly-granted global permissions. A no-op
     * submission (identical to the current set) succeeds without requiring/consuming a token
     * (REQ-14); any real change requires a valid token and emits one {@code AuditEvent} per
     * added/removed permission (REQ-15). REQ-16: rejected outright against a {@code STAFF_ADMIN}
     * target, regardless of token.
     */
    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    public void batchUpdatePermissions(
            Long userId, Set<GlobalPermission> permissions, String word) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());
        rejectAdminTarget(user);

        Set<GlobalPermission> current =
                new HashSet<>(
                        directGlobalPermissionGrantRepository.findByUser(user).stream()
                                .map(DirectGlobalPermissionGrant::getPermission)
                                .toList());
        Set<GlobalPermission> submitted = permissions == null ? Set.of() : permissions;

        Set<GlobalPermission> added = new HashSet<>(submitted);
        added.removeAll(current);
        Set<GlobalPermission> removed = new HashSet<>(current);
        removed.removeAll(submitted);

        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }

        if (!deletionConfirmationTokenService.validateAndConsume(
                BATCH_RESOURCE_TYPE, userId.toString(), currentActor(), word)) {
            throw new DeletionConfirmationInvalidException();
        }

        for (GlobalPermission permission : added) {
            directGlobalPermissionGrantRepository.save(
                    new DirectGlobalPermissionGrant(user, permission));
            writeBatchAuditEvent(userId, "grant", permission);
        }

        for (GlobalPermission permission : removed) {
            directGlobalPermissionGrantRepository
                    .findByUserAndPermission(user, permission)
                    .ifPresent(directGlobalPermissionGrantRepository::delete);
            writeBatchAuditEvent(userId, "revoke", permission);
        }
    }

    private void writeBatchAuditEvent(Long userId, String change, GlobalPermission permission) {
        auditEventWriter.write(
                new AuditEvent(
                        currentActor().getId(),
                        null,
                        "staff.permission.batch_update",
                        "DirectGlobalPermissionGrant",
                        userId + ":" + change + ":" + permission,
                        AuditOutcome.SUCCESS));
    }

    /**
     * REQ-2/8/17-19: rejects any grant/access-group/batch-update mutation whose target is a {@code
     * STAFF_ADMIN} — demote/delete are the only paths allowed to touch an admin-tier target.
     */
    private void rejectAdminTarget(User user) {
        if (user.getGlobalRole() == GlobalRole.STAFF_ADMIN) {
            throw new PermissionDeniedException();
        }
    }

    private void requireNotSelfTarget(Long targetUserId) {
        if (targetUserId != null && targetUserId.equals(currentActor().getId())) {
            throw new PermissionDeniedException();
        }
    }

    /**
     * Locks every current {@code STAFF_ADMIN} row (including {@code targetUserId}'s, if still one)
     * and rejects if none of the others remain — closes the TOCTOU window a plain {@code COUNT}
     * read-then-write would leave open (PLAN.md).
     */
    private void requireNotLastStaffAdmin(Long targetUserId) {
        List<User> admins = userRepository.findByGlobalRoleForUpdate(GlobalRole.STAFF_ADMIN);
        boolean anyOtherAdminRemains =
                admins.stream().anyMatch(admin -> !admin.getId().equals(targetUserId));

        if (!anyOtherAdminRemains) {
            throw new LastAdminRemainingException();
        }
    }

    /**
     * REQ-2/REQ-3 (user-role-selection-at-creation): only a caller who is themselves {@code
     * STAFF_ADMIN} may create another {@code STAFF_ADMIN} -- no permission-grant substitution, a
     * {@code STAFF} caller holding {@code STAFF_USER_CREATE}/{@code STAFF_PERMISSION_MANAGE}/any
     * other grant still fails this check. Reused verbatim by {@code
     * staff-rbac-management-operations}'s demotion/deletion/promotion paths, per PLAN.md.
     */
    private void requireCallerIsStaffAdmin() {
        if (currentActor().getGlobalRole() != GlobalRole.STAFF_ADMIN) {
            throw new PermissionDeniedException();
        }
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
