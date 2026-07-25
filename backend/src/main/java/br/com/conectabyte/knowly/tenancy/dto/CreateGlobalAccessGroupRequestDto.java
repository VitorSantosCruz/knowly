package br.com.conectabyte.knowly.tenancy.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateGlobalAccessGroupRequestDto(@NotBlank String name) {}
