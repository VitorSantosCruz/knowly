package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import java.util.List;

public record StaffUserDetailDto(
        Long userId,
        String email,
        GlobalRole globalRole,
        List<GlobalPermission> directPermissions,
        List<GlobalAccessGroupDto> accessGroups,
        List<GlobalPermission> effectivePermissions,
        boolean isLastAdminOfType) {}
