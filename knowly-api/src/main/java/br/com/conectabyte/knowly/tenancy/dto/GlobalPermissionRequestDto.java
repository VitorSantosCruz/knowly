package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import jakarta.validation.constraints.NotNull;

public record GlobalPermissionRequestDto(@NotNull GlobalPermission permission) {}
