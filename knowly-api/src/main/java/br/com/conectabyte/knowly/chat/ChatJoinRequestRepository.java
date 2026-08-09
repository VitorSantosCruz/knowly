package br.com.conectabyte.knowly.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatJoinRequestRepository extends JpaRepository<ChatJoinRequest, Long> {

    List<ChatJoinRequest> findByConversationId(Long conversationId);

    List<ChatJoinRequest> findByConversationIdAndStatus(
            Long conversationId, ChatJoinRequestStatus status);

    Optional<ChatJoinRequest> findByConversationIdAndRequesterIdAndStatus(
            Long conversationId, Long requesterId, ChatJoinRequestStatus status);
}
