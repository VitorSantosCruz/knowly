package br.com.conectabyte.knowly.deletion.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteConfirmationRequestDto(@NotBlank String word) {}
