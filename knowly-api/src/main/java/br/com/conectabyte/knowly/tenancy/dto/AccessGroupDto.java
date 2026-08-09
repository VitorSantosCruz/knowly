package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.AccessGroup;
import br.com.conectabyte.knowly.tenancy.Permission;
import java.util.List;

public record AccessGroupDto(Long id, String name, List<Permission> permissions) {

    /**
     * role-permission-revoke REQ-11: callers that don't have a pre-fetched permission list (e.g.
     * {@code getMemberDetail}'s per-membership access-group mapping) get an empty list rather than
     * an N+1 lookup here -- only {@code listAccessGroups} bulk-fetches and passes permissions via
     * {@link #from(AccessGroup, List)}.
     */
    public static AccessGroupDto from(AccessGroup accessGroup) {
        return new AccessGroupDto(accessGroup.getId(), accessGroup.getName(), List.of());
    }

    public static AccessGroupDto from(AccessGroup accessGroup, List<Permission> permissions) {
        return new AccessGroupDto(accessGroup.getId(), accessGroup.getName(), permissions);
    }
}
