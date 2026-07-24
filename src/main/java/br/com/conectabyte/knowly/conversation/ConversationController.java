package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.RequiresPermission;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.dto.ConversationDetailDto;
import br.com.conectabyte.knowly.conversation.dto.ConversationSummaryDto;
import br.com.conectabyte.knowly.tenancy.Permission;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final UserRepository userRepository;

    public ConversationController(
            ConversationService conversationService, UserRepository userRepository) {
        this.conversationService = conversationService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @RequiresPermission(Permission.CONVERSATION_USE)
    @AuditLog(action = "conversation.create", resourceType = "Conversation")
    public ResponseEntity<ConversationSummaryDto> create(@PathVariable Long tenantId) {
        ConversationSummaryDto conversation = conversationService.create(currentUser(), tenantId);

        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    @GetMapping
    @RequiresPermission(Permission.CONVERSATION_USE)
    @AuditLog(action = "conversation.list", resourceType = "Conversation")
    public ResponseEntity<List<ConversationSummaryDto>> list(@PathVariable Long tenantId) {
        return ResponseEntity.ok(conversationService.list(currentUser(), tenantId));
    }

    @GetMapping("/{conversationId}")
    @RequiresPermission(Permission.CONVERSATION_USE)
    @AuditLog(
            action = "conversation.view",
            resourceType = "Conversation",
            resourceIdExpression = "#conversationId")
    public ResponseEntity<ConversationDetailDto> get(
            @PathVariable Long tenantId, @PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.get(currentUser(), tenantId, conversationId));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}
