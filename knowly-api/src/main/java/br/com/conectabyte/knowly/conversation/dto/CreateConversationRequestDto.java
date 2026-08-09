package br.com.conectabyte.knowly.conversation.dto;

import br.com.conectabyte.knowly.icon.IconKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConversationRequestDto(@NotBlank @Size(max = 255) String title, IconKey icon) {}
