package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.Permission;
import jakarta.validation.constraints.NotNull;

public record PermissionRequestDto(@NotNull Permission permission) {}
