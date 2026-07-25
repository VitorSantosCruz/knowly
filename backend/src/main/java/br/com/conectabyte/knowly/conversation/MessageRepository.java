package br.com.conectabyte.knowly.conversation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    long countByConversation_Tenant_IdAndRole(Long tenantId, MessageRole role);
}
