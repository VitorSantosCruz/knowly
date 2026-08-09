package br.com.conectabyte.knowly.chat.dto;

import java.util.List;

public record ChatAddParticipantsResultDto(
        ChatConversationDetailDto conversation, List<ChatParticipantRejectionDto> rejected) {}
