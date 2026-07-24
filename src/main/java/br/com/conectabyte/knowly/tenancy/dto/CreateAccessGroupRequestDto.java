package br.com.conectabyte.knowly.tenancy.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAccessGroupRequestDto(@NotBlank String name) {}
