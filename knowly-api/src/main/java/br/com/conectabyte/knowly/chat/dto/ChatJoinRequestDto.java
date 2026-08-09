package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.ChatJoinRequest;
import br.com.conectabyte.knowly.chat.ChatJoinRequestStatus;
import java.time.Instant;

public record ChatJoinRequestDto(
        Long id,
        Long conversationId,
        Long requesterUserId,
        String requesterNickname,
        ChatJoinRequestStatus status,
        Instant decidedAt) {

    public static ChatJoinRequestDto from(ChatJoinRequest request, String requesterNickname) {
        return new ChatJoinRequestDto(
                request.getId(),
                request.getConversation().getId(),
                request.getRequester().getId(),
                requesterNickname,
                request.getStatus(),
                request.getDecidedAt());
    }
}
