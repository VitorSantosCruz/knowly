package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.ProfileFieldsDto;
import br.com.conectabyte.knowly.identity.dto.UserProfileDto;
import br.com.conectabyte.knowly.identity.exception.UserNotFoundException;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.PermissionService;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * View/direct-edit of a {@code User}'s personal-data fields (REQ-8..14a), per
 * specify/features/identity-profile-model/PLAN.md. Deliberately not {@code @Transactional} on any
 * method here, mirroring {@code NotificationService}'s existing precedent: the admin-bypass/
 * shared-tenant-permission checks below must see every one of the caller's/target's memberships
 * across every tenant, not just whichever tenant happens to be the caller's currently-active one --
 * {@code TenantFilterAspect} only ever enables {@code TenantFilter} around
 * {@code @Transactional}-annotated methods, so staying outside that keeps these cross-tenant reads
 * unfiltered.
 *
 * <p>The full REQ-9..14a decision tree is implemented as explicit checks here rather than only
 * {@code @RequiresPermission}/{@code @RequiresGlobalPermission}, because the actual rule set
 * depends on whose record is targeted (self vs. other) and on admin-role shortcuts that bypass the
 * permission system entirely -- no single annotation expresses "admin bypass OR
 * tenant-permission-with-self-exclusion OR global-permission-with-self-exclusion."
 */
@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final PermissionService permissionService;
    private final GlobalPermissionService globalPermissionService;
    private final BlindIndexService blindIndexService;

    public UserProfileService(
            UserRepository userRepository,
            TenantMembershipRepository tenantMembershipRepository,
            PermissionService permissionService,
            GlobalPermissionService globalPermissionService,
            BlindIndexService blindIndexService) {
        this.userRepository = userRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.permissionService = permissionService;
        this.globalPermissionService = globalPermissionService;
        this.blindIndexService = blindIndexService;
    }

    /** REQ-8: the caller's own full profile detail, always allowed. */
    public UserProfileDto getOwnProfile(User caller) {
        return toDto(caller);
    }

    /** REQ-9/10/10a/10b/10c: view another user's full profile detail. */
    @AuditLog(
            action = "identity.profile.view",
            resourceType = "User",
            resourceIdExpression = "#targetUserId")
    public UserProfileDto getProfile(User caller, Long targetUserId) {
        User target = requireUser(targetUserId);

        if (caller.getId().equals(target.getId())) {
            return toDto(target);
        }

        if (caller.getGlobalRole() == GlobalRole.STAFF_ADMIN) {
            return toDto(target);
        }

        if (isMemberAdminOfSharedTenant(caller, target)) {
            return toDto(target);
        }

        if (hasSharedTenantPermission(caller, target, Permission.PROFILE_VIEW)) {
            return toDto(target);
        }

        if (caller.getGlobalRole() == GlobalRole.STAFF
                && globalPermissionService.hasPermission(caller, GlobalPermission.PROFILE_VIEW)) {
            return toDto(target);
        }

        throw new PermissionDeniedException();
    }

    /** REQ-11/12/13/13a/14/14a: directly edit a user's personal-data fields. */
    @AuditLog(
            action = "identity.profile.edit",
            resourceType = "User",
            resourceIdExpression = "#targetUserId")
    public UserProfileDto directEdit(User caller, Long targetUserId, ProfileFieldsDto fields) {
        User target = requireUser(targetUserId);

        if (!hasDirectEditRight(caller, target)) {
            throw new PermissionDeniedException();
        }

        applyFields(target, fields);
        userRepository.save(target);

        return toDto(target);
    }

    /**
     * REQ-11/12/13/13a/14/14a decision tree, shared by {@link #directEdit} and (against the
     * original requester as target) {@code ProfileEditRequestService#approveEditRequest}/{@code
     * #rejectEditRequest} (REQ-19). Package-private so {@code ProfileEditRequestService} (same
     * package) can re-run the same check at approval time.
     */
    boolean hasDirectEditRight(User caller, User target) {
        boolean isSelf = caller.getId().equals(target.getId());

        boolean allowed =
                caller.getGlobalRole() == GlobalRole.STAFF_ADMIN
                        || isMemberAdminOfSharedTenant(caller, target);

        // REQ-13a/14a: a tenant/global PROFILE_EDIT holder (not admin) may only edit *others*
        // directly -- their own record must go through the self-requested-edit flow (REQ-15).
        if (!allowed && !isSelf) {
            allowed =
                    hasSharedTenantPermission(caller, target, Permission.PROFILE_EDIT)
                            || (caller.getGlobalRole() == GlobalRole.STAFF
                                    && globalPermissionService.hasPermission(
                                            caller, GlobalPermission.PROFILE_EDIT));
        }

        return allowed;
    }

    /**
     * The single choke point every direct-edit/approve call site routes through: plain fields are
     * set directly, {@code cpf}/{@code rg} are additionally routed through {@link
     * BlindIndexService} in the same call so the blind-index columns are never independently
     * editable (SPEC's "Resolved" section). Package-private so {@link ProfileEditRequestService}
     * (same package) reuses it for approvals rather than duplicating the write.
     */
    void applyFields(User target, ProfileFieldsDto fields) {
        if (fields.fullName() != null) {
            target.setFullName(fields.fullName());
        }
        if (fields.address() != null) {
            target.setAddress(fields.address());
        }
        if (fields.phone() != null) {
            target.setPhone(fields.phone());
        }
        if (fields.cpf() != null) {
            target.setCpf(fields.cpf());
            target.setCpfBlindIndex(blindIndexService.hmac(fields.cpf()));
        }
        if (fields.rg() != null) {
            target.setRg(fields.rg());
            target.setRgBlindIndex(blindIndexService.hmac(fields.rg()));
        }
    }

    /** REQ-11: is the caller MEMBER_ADMIN of any tenant the target is also an active member of. */
    boolean isMemberAdminOfSharedTenant(User caller, User target) {
        Set<Long> callerAdminTenantIds =
                tenantMembershipRepository.findByUserAndActiveTrue(caller).stream()
                        .filter(membership -> membership.getRole() == MembershipRole.MEMBER_ADMIN)
                        .map(membership -> membership.getTenant().getId())
                        .collect(Collectors.toSet());

        if (callerAdminTenantIds.isEmpty()) {
            return false;
        }

        return tenantMembershipRepository.findByUserAndActiveTrue(target).stream()
                .map(membership -> membership.getTenant().getId())
                .anyMatch(callerAdminTenantIds::contains);
    }

    /**
     * REQ-10/13: does the caller hold {@code permission} in a tenant the target also belongs to.
     */
    boolean hasSharedTenantPermission(User caller, User target, Permission permission) {
        Set<Long> targetTenantIds =
                tenantMembershipRepository.findByUserAndActiveTrue(target).stream()
                        .map(membership -> membership.getTenant().getId())
                        .collect(Collectors.toSet());

        return tenantMembershipRepository.findByUserAndActiveTrue(caller).stream()
                .filter(membership -> targetTenantIds.contains(membership.getTenant().getId()))
                .anyMatch(membership -> permissionService.hasPermission(membership, permission));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(UserNotFoundException::new);
    }

    private UserProfileDto toDto(User user) {
        ProfileFieldsDto fields =
                new ProfileFieldsDto(
                        user.getFullName(),
                        user.getAddress(),
                        user.getRg(),
                        user.getCpf(),
                        user.getPhone());

        return UserProfileDto.of(user.getId(), user.getEmail(), fields);
    }
}
