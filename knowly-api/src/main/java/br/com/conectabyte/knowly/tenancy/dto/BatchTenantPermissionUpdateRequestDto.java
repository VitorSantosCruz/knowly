package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.Permission;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Tenant-scope batch permission update request -- same shape/reasoning as {@link
 * BatchPermissionUpdateRequestDto}, {@code Permission} instead of {@code GlobalPermission}.
 */
public record BatchTenantPermissionUpdateRequestDto(
        @NotNull Set<Permission> permissions, String word) {}
