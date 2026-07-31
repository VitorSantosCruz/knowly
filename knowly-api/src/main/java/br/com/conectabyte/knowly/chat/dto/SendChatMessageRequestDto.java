package br.com.conectabyte.knowly.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record SendChatMessageRequestDto(@NotBlank String content) {}
