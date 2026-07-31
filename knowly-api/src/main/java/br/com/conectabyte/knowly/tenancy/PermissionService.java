package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final DirectPermissionGrantRepository directPermissionGrantRepository;
    private final UserAccessGroupRepository userAccessGroupRepository;
    private final AccessGroupPermissionRepository accessGroupPermissionRepository;
    private final TenantMembershipRepository tenantMembershipRepository;

    public PermissionService(
            DirectPermissionGrantRepository directPermissionGrantRepository,
            UserAccessGroupRepository userAccessGroupRepository,
            AccessGroupPermissionRepository accessGroupPermissionRepository,
            TenantMembershipRepository tenantMembershipRepository) {
        this.directPermissionGrantRepository = directPermissionGrantRepository;
        this.userAccessGroupRepository = userAccessGroupRepository;
        this.accessGroupPermissionRepository = accessGroupPermissionRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
    }

    public Set<Permission> effectivePermissions(TenantMembership membership) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);

        directPermissionGrantRepository.findByTenantMembership(membership).stream()
                .map(DirectPermissionGrant::getPermission)
                .forEach(permissions::add);

        List<AccessGroup> groups =
                userAccessGroupRepository.findByTenantMembership(membership).stream()
                        .map(UserAccessGroup::getAccessGroup)
                        .collect(Collectors.toList());

        accessGroupPermissionRepository.findByAccessGroupIn(groups).stream()
                .map(AccessGroupPermission::getPermission)
                .forEach(permissions::add);

        return permissions;
    }

    public boolean hasPermission(TenantMembership membership, Permission permission) {
        return effectivePermissions(membership).contains(permission);
    }

    /**
     * REQ-19 ({@code user-profile-v2}): does the given user hold {@code permission} in any of their
     * (active) tenant memberships, not just the currently-active one. Mirrors the "look across
     * every membership" pattern already established by {@code ProfileEditRequestService}.
     */
    public boolean hasPermissionInAnyTenant(User user, Permission permission) {
        return tenantMembershipRepository.findByUserAndActiveTrue(user).stream()
                .anyMatch(membership -> hasPermission(membership, permission));
    }
}
