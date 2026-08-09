package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.icon.IconKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameChatConversationRequestDto(
        @NotBlank @Size(max = 255) String title, IconKey icon) {}
