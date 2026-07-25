package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import java.util.List;

public record StaffUserDetailDto(
        Long userId,
        String email,
        List<GlobalPermission> directPermissions,
        List<GlobalAccessGroupDto> accessGroups,
        List<GlobalPermission> effectivePermissions) {}
