package br.com.conectabyte.knowly.conversation.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequestDto(@NotBlank String content) {}
