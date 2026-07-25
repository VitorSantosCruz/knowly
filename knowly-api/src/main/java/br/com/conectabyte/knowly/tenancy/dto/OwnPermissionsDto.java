package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.Permission;
import java.util.List;

public record OwnPermissionsDto(List<Permission> permissions) {}
