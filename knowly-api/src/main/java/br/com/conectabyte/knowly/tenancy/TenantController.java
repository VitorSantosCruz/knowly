package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.dto.AccessGroupDto;
import br.com.conectabyte.knowly.tenancy.dto.AddMemberRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.AnyTenantPermissionDto;
import br.com.conectabyte.knowly.tenancy.dto.CreateAccessGroupRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.CreateTenantRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.MemberDetailDto;
import br.com.conectabyte.knowly.tenancy.dto.MemberDto;
import br.com.conectabyte.knowly.tenancy.dto.OwnPermissionsDto;
import br.com.conectabyte.knowly.tenancy.dto.PageResponseDto;
import br.com.conectabyte.knowly.tenancy.dto.PermissionRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.SwitchActiveTenantRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.TenantMembershipDto;
import br.com.conectabyte.knowly.tenancy.dto.TenantSummaryDto;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final PermissionService permissionService;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    public TenantController(
            TenantService tenantService,
            PermissionService permissionService,
            UserRepository userRepository,
            TenantContext tenantContext) {
        this.tenantService = tenantService;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/memberships")
    public ResponseEntity<List<TenantMembershipDto>> listOwnMemberships() {
        User user = currentUser();
        Long activeTenantId = tenantContext.getActiveTenantId().orElse(null);
        List<TenantMembershipDto> memberships =
                tenantService.listOwnMemberships(user).stream()
                        .map(
                                membership ->
                                        TenantMembershipDto.from(
                                                membership,
                                                membership
                                                        .getTenant()
                                                        .getId()
                                                        .equals(activeTenantId)))
                        .toList();

        return ResponseEntity.ok(memberships);
    }

    @GetMapping("/permissions")
    public ResponseEntity<OwnPermissionsDto> ownPermissions() {
        User user = currentUser();
        Long tenantId =
                tenantContext.getActiveTenantId().orElseThrow(TenantAccessDeniedException::new);
        List<Permission> permissions =
                tenantService.ownEffectivePermissions(user, tenantId, tenantContext.isStaffAdmin());

        return ResponseEntity.ok(new OwnPermissionsDto(permissions));
    }

    /**
     * REQ-19 ({@code user-profile-v2}): does the caller hold {@code permission} in any of their
     * tenant memberships (not just the active one), always scoped to the calling session's own user
     * (never another user). Placement under {@code /api/tenants/**} is incidental -- this is a
     * {@code GET}/no-state-change endpoint and must not be assumed to inherit this prefix's legacy
     * CSRF exemption; a future state-changing endpoint added under this prefix must not copy this
     * one as precedent for skipping CSRF protection.
     */
    @GetMapping("/permissions/any-tenant")
    public ResponseEntity<AnyTenantPermissionDto> hasPermissionInAnyTenant(
            @RequestParam Permission permission) {
        User user = currentUser();
        boolean granted =
                tenantContext.isStaffAdmin()
                        || permissionService.hasPermissionInAnyTenant(user, permission);

        return ResponseEntity.ok(new AnyTenantPermissionDto(granted));
    }

    @GetMapping
    public ResponseEntity<PageResponseDto<TenantSummaryDto>> listAllTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(tenantService.listAllTenants(currentUser(), page, size, search));
    }

    @PostMapping("/active")
    @AuditLog(
            action = "tenant.active_tenant.switch",
            resourceType = "Tenant",
            resourceIdExpression = "#request.tenantId()")
    public ResponseEntity<Void> switchActiveTenant(
            @Valid @RequestBody SwitchActiveTenantRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        User user = currentUser();
        List<GrantedAuthority> authorities;

        if (tenantContext.isStaff()) {
            tenantService.requireTenant(user, request.tenantId());
            authorities = TenantAuthorityFactory.forStaff(user.getGlobalRole());
        } else {
            TenantMembership membership =
                    tenantService.requireActiveMembership(user, request.tenantId());
            authorities =
                    TenantAuthorityFactory.forMembership(
                            membership, permissionService.effectivePermissions(membership));
        }

        var authentication =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, httpRequest, httpResponse);

        HttpSession session = httpRequest.getSession(true);
        session.removeAttribute(TenantSessionKeys.SELECTION_PENDING);
        session.setAttribute(TenantSessionKeys.ACTIVE_TENANT_ID, request.tenantId());

        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<Void> createTenant(@Valid @RequestBody CreateTenantRequestDto request) {
        tenantService.createTenant(currentUser(), request.name(), request.adminEmail());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tenantId}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable Long tenantId, @Valid @RequestBody AddMemberRequestDto request) {
        tenantService.addMember(currentUser(), tenantId, request.email(), request.role());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{tenantId}/members")
    public ResponseEntity<List<MemberDto>> listMembers(@PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.listMembers(currentUser(), tenantId));
    }

    @GetMapping("/{tenantId}/members/{membershipId}")
    public ResponseEntity<MemberDetailDto> getMember(
            @PathVariable Long tenantId, @PathVariable Long membershipId) {
        return ResponseEntity.ok(
                tenantService.getMemberDetail(currentUser(), tenantId, membershipId));
    }

    @DeleteMapping("/{tenantId}/members/{membershipId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long tenantId, @PathVariable Long membershipId) {
        tenantService.removeMember(currentUser(), tenantId, membershipId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tenantId}/members/{membershipId}/permissions")
    public ResponseEntity<Void> grantPermission(
            @PathVariable Long tenantId,
            @PathVariable Long membershipId,
            @Valid @RequestBody PermissionRequestDto request) {
        tenantService.grantPermission(currentUser(), tenantId, membershipId, request.permission());

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{tenantId}/members/{membershipId}/permissions/{permission}")
    public ResponseEntity<Void> revokePermission(
            @PathVariable Long tenantId,
            @PathVariable Long membershipId,
            @PathVariable Permission permission) {
        tenantService.revokePermission(currentUser(), tenantId, membershipId, permission);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{tenantId}/access-groups")
    public ResponseEntity<List<AccessGroupDto>> listAccessGroups(@PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.listAccessGroups(currentUser(), tenantId));
    }

    @PostMapping("/{tenantId}/access-groups")
    public ResponseEntity<Void> createAccessGroup(
            @PathVariable Long tenantId, @Valid @RequestBody CreateAccessGroupRequestDto request) {
        tenantService.createAccessGroup(currentUser(), tenantId, request.name());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tenantId}/access-groups/{accessGroupId}/permissions")
    public ResponseEntity<Void> grantAccessGroupPermission(
            @PathVariable Long tenantId,
            @PathVariable Long accessGroupId,
            @Valid @RequestBody PermissionRequestDto request) {
        tenantService.grantAccessGroupPermission(
                currentUser(), tenantId, accessGroupId, request.permission());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}")
    public ResponseEntity<Void> assignAccessGroup(
            @PathVariable Long tenantId,
            @PathVariable Long membershipId,
            @PathVariable Long accessGroupId) {
        tenantService.assignAccessGroup(currentUser(), tenantId, membershipId, accessGroupId);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{tenantId}/members/{membershipId}/access-groups/{accessGroupId}")
    public ResponseEntity<Void> unassignAccessGroup(
            @PathVariable Long tenantId,
            @PathVariable Long membershipId,
            @PathVariable Long accessGroupId) {
        tenantService.unassignAccessGroup(currentUser(), tenantId, membershipId, accessGroupId);

        return ResponseEntity.ok().build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}
