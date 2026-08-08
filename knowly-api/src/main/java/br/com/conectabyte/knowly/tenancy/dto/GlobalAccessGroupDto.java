package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.GlobalAccessGroup;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import java.util.List;

public record GlobalAccessGroupDto(Long id, String name, List<GlobalPermission> permissions) {

    /**
     * role-permission-revoke REQ-11: staff-scope mirror of {@code AccessGroupDto#from(AccessGroup)}
     * -- callers without a pre-fetched permission list get an empty list; only {@code
     * listAccessGroups} bulk-fetches and passes permissions via {@link #from(GlobalAccessGroup,
     * List)}.
     */
    public static GlobalAccessGroupDto from(GlobalAccessGroup globalAccessGroup) {
        return new GlobalAccessGroupDto(
                globalAccessGroup.getId(), globalAccessGroup.getName(), List.of());
    }

    public static GlobalAccessGroupDto from(
            GlobalAccessGroup globalAccessGroup, List<GlobalPermission> permissions) {
        return new GlobalAccessGroupDto(
                globalAccessGroup.getId(), globalAccessGroup.getName(), permissions);
    }
}
