package br.com.conectabyte.knowly.tenancy.dto;

import jakarta.validation.constraints.NotNull;

public record SwitchActiveTenantRequestDto(@NotNull Long tenantId) {}
