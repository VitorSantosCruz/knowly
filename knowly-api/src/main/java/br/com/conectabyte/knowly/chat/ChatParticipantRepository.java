package br.com.conectabyte.knowly.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    List<ChatParticipant> findByConversationId(Long conversationId);

    List<ChatParticipant> findByUserId(Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);
}
