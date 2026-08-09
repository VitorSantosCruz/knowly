package br.com.conectabyte.knowly.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AddChatParticipantsRequestDto(@NotEmpty List<Long> userIds) {}
