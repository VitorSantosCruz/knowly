package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.dto.AccessGroupDto;
import br.com.conectabyte.knowly.tenancy.dto.MemberDetailDto;
import br.com.conectabyte.knowly.tenancy.dto.MemberDto;
import br.com.conectabyte.knowly.tenancy.dto.TenantSummaryDto;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRepository userRepository;
    private final DirectPermissionGrantRepository directPermissionGrantRepository;
    private final AccessGroupRepository accessGroupRepository;
    private final AccessGroupPermissionRepository accessGroupPermissionRepository;
    private final UserAccessGroupRepository userAccessGroupRepository;
    private final PermissionService permissionService;

    public TenantService(
            TenantRepository tenantRepository,
            TenantMembershipRepository tenantMembershipRepository,
            UserRepository userRepository,
            DirectPermissionGrantRepository directPermissionGrantRepository,
            AccessGroupRepository accessGroupRepository,
            AccessGroupPermissionRepository accessGroupPermissionRepository,
            UserAccessGroupRepository userAccessGroupRepository,
            PermissionService permissionService) {
        this.tenantRepository = tenantRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.userRepository = userRepository;
        this.directPermissionGrantRepository = directPermissionGrantRepository;
        this.accessGroupRepository = accessGroupRepository;
        this.accessGroupPermissionRepository = accessGroupPermissionRepository;
        this.userAccessGroupRepository = userAccessGroupRepository;
        this.permissionService = permissionService;
    }

    /**
     * Resolves how a freshly-authenticated user's session should start out, per REQ-3/4/5.
     * Deliberately not {@code @Transactional}: this call must see the user's memberships across
     * every tenant, and — since it's scoped by user identity, not by an arbitrary listing — it's
     * safe to run through Spring Data's own default per-call transaction rather than one where
     * TenantFilterAspect has enabled the tenant filter (which would otherwise filter out every
     * membership before a tenant is even chosen).
     */
    public TenantSessionOutcome resolveSessionOutcome(User user) {
        if (user.getGlobalRole() == GlobalRole.STAFF) {
            return new TenantSessionOutcome.Staff();
        }

        List<TenantMembership> memberships =
                tenantMembershipRepository.findByUserAndActiveTrue(user);

        if (memberships.size() == 1) {
            return new TenantSessionOutcome.AutoSelected(memberships.get(0).getTenant().getId());
        }

        return new TenantSessionOutcome.SelectionPending();
    }

    /**
     * Lists the caller's own active memberships (for the tenant picker / switch-tenant menu). Same
     * reasoning as {@link #resolveSessionOutcome}: intentionally not {@code @Transactional}, this
     * is scoped by the caller's own identity.
     */
    public List<TenantMembership> listOwnMemberships(User user) {
        return tenantMembershipRepository.findByUserAndActiveTrue(user);
    }

    /**
     * Validates that the user actually holds an active membership in the requested tenant (REQ-7).
     * Same reasoning as {@link #resolveSessionOutcome}: intentionally not {@code @Transactional},
     * the lookup is scoped by (user, tenant) explicitly.
     */
    public TenantMembership requireActiveMembership(User user, Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        return tenantMembershipRepository
                .findByUserAndTenant(user, tenant)
                .filter(TenantMembership::isActive)
                .orElseThrow(TenantAccessDeniedException::new);
    }

    /**
     * Every tenant in the system — staff-only, powers a staff "act as this tenant" picker (staff
     * have no memberships of their own to pick from, unlike a regular multi-membership user).
     */
    @Transactional(readOnly = true)
    public List<TenantSummaryDto> listAllTenants(User actor) {
        requireStaff(actor);

        return tenantRepository.findAll().stream().map(TenantSummaryDto::from).toList();
    }

    /** Confirms a tenant exists, for staff switching to act as it without holding a membership. */
    @Transactional(readOnly = true)
    public Tenant requireTenant(Long tenantId) {
        return tenantRepository.findById(tenantId).orElseThrow(TenantAccessDeniedException::new);
    }

    /**
     * The caller's own effective permissions in their active tenant — lets the frontend hide
     * actions it can't perform instead of showing them and letting a 403 explain why. Staff get
     * every permission, consistent with {@code PermissionAspect} bypassing the check for them.
     */
    @Transactional(readOnly = true)
    public List<Permission> ownEffectivePermissions(User user, Long tenantId, boolean staff) {
        if (staff) {
            return List.of(Permission.values());
        }

        TenantMembership membership = requireActiveMembership(user, tenantId);

        return List.copyOf(permissionService.effectivePermissions(membership));
    }

    /** REQ-10: only staff create tenants, always atomically with a first admin. */
    @Transactional
    @AuditLog(action = "tenant.create", resourceType = "Tenant")
    public Tenant createTenant(User actor, String tenantName, String adminEmail) {
        requireStaff(actor);

        Tenant tenant = tenantRepository.save(new Tenant(tenantName));
        User admin =
                userRepository
                        .findByEmailIgnoreCase(adminEmail)
                        .orElseGet(() -> userRepository.save(new User(adminEmail)));

        tenantMembershipRepository.save(new TenantMembership(admin, tenant, MembershipRole.ADMIN));

        return tenant;
    }

    /** REQ-9/16: tenant admin (own tenant) or staff (any tenant) can add members. */
    @Transactional
    @AuditLog(action = "tenant.member.add", resourceType = "TenantMembership")
    public TenantMembership addMember(
            User actor, Long tenantId, String email, MembershipRole role) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(TenantAccessDeniedException::new);
        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseGet(() -> userRepository.save(new User(email)));

        TenantMembership membership =
                tenantMembershipRepository
                        .findByUserAndTenant(user, tenant)
                        .orElseGet(() -> new TenantMembership(user, tenant, role));
        membership.setRole(role);
        membership.setActive(true);

        return tenantMembershipRepository.save(membership);
    }

    /** REQ-9/19: always a soft removal, never a hard delete. */
    @Transactional
    @AuditLog(action = "tenant.member.remove", resourceType = "TenantMembership")
    public void removeMember(User actor, Long tenantId, Long membershipId) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        membership.setActive(false);
        tenantMembershipRepository.save(membership);
    }

    /** REQ-14/16: direct permission grant, admin (own tenant) or staff only. */
    @Transactional
    @AuditLog(action = "tenant.permission.grant", resourceType = "DirectPermissionGrant")
    public void grantPermission(
            User actor, Long tenantId, Long membershipId, Permission permission) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        directPermissionGrantRepository
                .findByTenantMembershipAndPermission(membership, permission)
                .orElseGet(
                        () ->
                                directPermissionGrantRepository.save(
                                        new DirectPermissionGrant(membership, permission)));
    }

    /** REQ-14/16: revoke a direct permission grant, admin (own tenant) or staff only. */
    @Transactional
    @AuditLog(action = "tenant.permission.revoke", resourceType = "DirectPermissionGrant")
    public void revokePermission(
            User actor, Long tenantId, Long membershipId, Permission permission) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        directPermissionGrantRepository
                .findByTenantMembershipAndPermission(membership, permission)
                .ifPresent(directPermissionGrantRepository::delete);
    }

    /** REQ-13: tenant-scoped, admin-defined access group. */
    @Transactional
    @AuditLog(action = "tenant.access_group.create", resourceType = "AccessGroup")
    public AccessGroup createAccessGroup(User actor, Long tenantId, String name) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(TenantAccessDeniedException::new);

        return accessGroupRepository.save(new AccessGroup(tenant, name));
    }

    /** REQ-13/16: assign a permission to an access group. */
    @Transactional
    @AuditLog(
            action = "tenant.access_group.grant_permission",
            resourceType = "AccessGroupPermission")
    public void grantAccessGroupPermission(
            User actor, Long tenantId, Long accessGroupId, Permission permission) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        AccessGroup accessGroup =
                accessGroupRepository
                        .findById(accessGroupId)
                        .orElseThrow(TenantAccessDeniedException::new);
        accessGroupPermissionRepository
                .findByAccessGroupAndPermission(accessGroup, permission)
                .orElseGet(
                        () ->
                                accessGroupPermissionRepository.save(
                                        new AccessGroupPermission(accessGroup, permission)));
    }

    /** REQ-14: assign a membership to an access group, taking effect immediately. */
    @Transactional
    @AuditLog(action = "tenant.member.access_group.assign", resourceType = "UserAccessGroup")
    public void assignAccessGroup(
            User actor, Long tenantId, Long membershipId, Long accessGroupId) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        AccessGroup accessGroup =
                accessGroupRepository
                        .findById(accessGroupId)
                        .orElseThrow(TenantAccessDeniedException::new);

        userAccessGroupRepository
                .findByTenantMembershipAndAccessGroup(membership, accessGroup)
                .orElseGet(
                        () ->
                                userAccessGroupRepository.save(
                                        new UserAccessGroup(membership, accessGroup)));
    }

    /** REQ-9/16: list a tenant's active members — admin (own tenant) or staff only. */
    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(User actor, Long tenantId) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        return tenantMembershipRepository.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(MemberDto::from)
                .toList();
    }

    /** REQ-13: list a tenant's access groups — admin (own tenant) or staff only. */
    @Transactional(readOnly = true)
    public List<AccessGroupDto> listAccessGroups(User actor, Long tenantId) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(TenantAccessDeniedException::new);

        return accessGroupRepository.findByTenant(tenant).stream()
                .map(AccessGroupDto::from)
                .toList();
    }

    /**
     * REQ-15/16: a member's direct/group/effective permissions — admin (own tenant) or staff only.
     */
    @Transactional(readOnly = true)
    public MemberDetailDto getMemberDetail(User actor, Long tenantId, Long membershipId) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);

        List<Permission> direct =
                directPermissionGrantRepository.findByTenantMembership(membership).stream()
                        .map(DirectPermissionGrant::getPermission)
                        .toList();
        List<AccessGroupDto> groups =
                userAccessGroupRepository.findByTenantMembership(membership).stream()
                        .map(UserAccessGroup::getAccessGroup)
                        .map(AccessGroupDto::from)
                        .toList();
        List<Permission> effective =
                permissionService.effectivePermissions(membership).stream().toList();

        return new MemberDetailDto(
                membership.getId(),
                membership.getUser().getEmail(),
                membership.getRole(),
                direct,
                groups,
                effective);
    }

    /** REQ-14/16: unassign a membership from an access group, admin (own tenant) or staff only. */
    @Transactional
    @AuditLog(action = "tenant.member.access_group.unassign", resourceType = "UserAccessGroup")
    public void unassignAccessGroup(
            User actor, Long tenantId, Long membershipId, Long accessGroupId) {
        requireAdminOfTenantOrStaff(actor, tenantId);

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        AccessGroup accessGroup =
                accessGroupRepository
                        .findById(accessGroupId)
                        .orElseThrow(TenantAccessDeniedException::new);

        userAccessGroupRepository
                .findByTenantMembershipAndAccessGroup(membership, accessGroup)
                .ifPresent(userAccessGroupRepository::delete);
    }

    private void requireStaff(User actor) {
        if (actor.getGlobalRole() != GlobalRole.STAFF) {
            throw new PermissionDeniedException();
        }
    }

    /** REQ-9/16: staff can manage any tenant; a tenant admin only their own. */
    private void requireAdminOfTenantOrStaff(User actor, Long tenantId) {
        if (actor.getGlobalRole() == GlobalRole.STAFF) {
            return;
        }

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        boolean isAdminOfTenant =
                tenantMembershipRepository
                        .findByUserAndTenant(actor, tenant)
                        .filter(TenantMembership::isActive)
                        .filter(membership -> membership.getRole() == MembershipRole.ADMIN)
                        .isPresent();

        if (!isAdminOfTenant) {
            throw new PermissionDeniedException();
        }
    }
}
