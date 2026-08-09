package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatGroupVisibility;
import jakarta.validation.constraints.NotNull;

public record ChangeChatVisibilityRequestDto(@NotNull ChatGroupVisibility visibility) {}
