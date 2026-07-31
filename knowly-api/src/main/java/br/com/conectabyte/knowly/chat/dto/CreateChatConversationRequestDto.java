package br.com.conectabyte.knowly.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateChatConversationRequestDto(
        @NotNull ChatConversationRequestKind kind,
        Long tenantId,
        String title,
        @NotEmpty List<Long> participantUserIds) {

    public enum ChatConversationRequestKind {
        DIRECT,
        GROUP
    }
}
