package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.AccessGroup;

public record AccessGroupDto(Long id, String name) {

    public static AccessGroupDto from(AccessGroup accessGroup) {
        return new AccessGroupDto(accessGroup.getId(), accessGroup.getName());
    }
}
