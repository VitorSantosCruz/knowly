package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import java.util.List;

public record MemberDetailDto(
        Long membershipId,
        Long userId,
        String email,
        MembershipRole role,
        List<Permission> directPermissions,
        List<AccessGroupDto> accessGroups,
        List<Permission> effectivePermissions) {}
