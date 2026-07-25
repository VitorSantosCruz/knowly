package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.GlobalAccessGroup;

public record GlobalAccessGroupDto(Long id, String name) {

    public static GlobalAccessGroupDto from(GlobalAccessGroup globalAccessGroup) {
        return new GlobalAccessGroupDto(globalAccessGroup.getId(), globalAccessGroup.getName());
    }
}
