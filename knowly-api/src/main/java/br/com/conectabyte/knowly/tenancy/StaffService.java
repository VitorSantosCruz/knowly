package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.RequiresGlobalPermission;
import br.com.conectabyte.knowly.auth.MailService;
import br.com.conectabyte.knowly.auth.OneTimePasswordService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.dto.GlobalAccessGroupDto;
import br.com.conectabyte.knowly.tenancy.dto.StaffUserDetailDto;
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

    public StaffService(
            UserRepository userRepository,
            DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository,
            GlobalAccessGroupRepository globalAccessGroupRepository,
            GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository,
            UserGlobalAccessGroupRepository userGlobalAccessGroupRepository,
            GlobalPermissionService globalPermissionService,
            OneTimePasswordService oneTimePasswordService,
            MailService mailService) {
        this.userRepository = userRepository;
        this.directGlobalPermissionGrantRepository = directGlobalPermissionGrantRepository;
        this.globalAccessGroupRepository = globalAccessGroupRepository;
        this.globalAccessGroupPermissionRepository = globalAccessGroupPermissionRepository;
        this.userGlobalAccessGroupRepository = userGlobalAccessGroupRepository;
        this.globalPermissionService = globalPermissionService;
        this.oneTimePasswordService = oneTimePasswordService;
        this.mailService = mailService;
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_USER_CREATE)
    @AuditLog(action = "staff.user.create", resourceType = "User")
    public User createStaffUser(String email) {
        enforceStaffCeiling(GlobalRole.STAFF);

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new StaffUserAlreadyExistsException();
        }

        User user = new User(email);
        user.setGlobalRole(GlobalRole.STAFF);
        user = userRepository.save(user);

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

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(action = "staff.permission.revoke", resourceType = "DirectGlobalPermissionGrant")
    public void revokePermission(Long userId, GlobalPermission permission) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());

        directGlobalPermissionGrantRepository
                .findByUserAndPermission(user, permission)
                .ifPresent(directGlobalPermissionGrantRepository::delete);
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

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)
    @AuditLog(action = "staff.member.access_group.unassign", resourceType = "UserGlobalAccessGroup")
    public void unassignAccessGroup(Long userId, Long accessGroupId) {
        User user = requireUser(userId);
        enforceStaffCeiling(user.getGlobalRole());
        GlobalAccessGroup accessGroup = requireAccessGroup(accessGroupId);

        userGlobalAccessGroupRepository
                .findByUserAndGlobalAccessGroup(user, accessGroup)
                .ifPresent(userGlobalAccessGroupRepository::delete);
    }

    private void enforceStaffCeiling(GlobalRole targetGlobalRole) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User actor =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(PermissionDeniedException::new);

        if (actor.getGlobalRole() == GlobalRole.STAFF
                && (targetGlobalRole == GlobalRole.STAFF
                        || targetGlobalRole == GlobalRole.STAFF_ADMIN)) {
            throw new PermissionDeniedException();
        }
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
