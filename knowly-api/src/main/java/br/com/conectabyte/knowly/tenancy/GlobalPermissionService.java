package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GlobalPermissionService {

    private final DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    private final UserGlobalAccessGroupRepository userGlobalAccessGroupRepository;
    private final GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository;

    public GlobalPermissionService(
            DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository,
            UserGlobalAccessGroupRepository userGlobalAccessGroupRepository,
            GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository) {
        this.directGlobalPermissionGrantRepository = directGlobalPermissionGrantRepository;
        this.userGlobalAccessGroupRepository = userGlobalAccessGroupRepository;
        this.globalAccessGroupPermissionRepository = globalAccessGroupPermissionRepository;
    }

    public Set<GlobalPermission> effectivePermissions(User user) {
        Set<GlobalPermission> permissions = EnumSet.noneOf(GlobalPermission.class);

        directGlobalPermissionGrantRepository.findByUserAndDeletedAtIsNull(user).stream()
                .map(DirectGlobalPermissionGrant::getPermission)
                .forEach(permissions::add);

        List<GlobalAccessGroup> groups =
                userGlobalAccessGroupRepository.findByUserAndDeletedAtIsNull(user).stream()
                        .map(UserGlobalAccessGroup::getGlobalAccessGroup)
                        .collect(Collectors.toList());

        globalAccessGroupPermissionRepository.findByGlobalAccessGroupIn(groups).stream()
                .map(GlobalAccessGroupPermission::getPermission)
                .forEach(permissions::add);

        return permissions;
    }

    public boolean hasPermission(User user, GlobalPermission permission) {
        return effectivePermissions(user).contains(permission);
    }
}
