package br.com.conectabyte.knowly.tenancy;

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

    public PermissionService(
            DirectPermissionGrantRepository directPermissionGrantRepository,
            UserAccessGroupRepository userAccessGroupRepository,
            AccessGroupPermissionRepository accessGroupPermissionRepository) {
        this.directPermissionGrantRepository = directPermissionGrantRepository;
        this.userAccessGroupRepository = userAccessGroupRepository;
        this.accessGroupPermissionRepository = accessGroupPermissionRepository;
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
}
