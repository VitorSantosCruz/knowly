package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.auth.exception.AuthenticatedUserNotFoundException;
import br.com.conectabyte.knowly.chat.dto.ChatConversationDetailDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessageDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessagePageDto;
import br.com.conectabyte.knowly.chat.dto.SendChatMessageRequestDto;
import br.com.conectabyte.knowly.chat.dto.SupportTicketDto;
import br.com.conectabyte.knowly.chat.dto.TransferTicketRequestDto;
import br.com.conectabyte.knowly.chat.exception.ChatConversationNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}/support")
public class SupportChannelController {

    private final SupportTicketService supportTicketService;
    private final ChatConversationService chatConversationService;
    private final UserRepository userRepository;

    public SupportChannelController(
            SupportTicketService supportTicketService,
            ChatConversationService chatConversationService,
            UserRepository userRepository) {
        this.supportTicketService = supportTicketService;
        this.chatConversationService = chatConversationService;
        this.userRepository = userRepository;
    }

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicketDto> openTicket(@PathVariable Long tenantId) {
        SupportTicketDto ticket = supportTicketService.openTicket(currentUser(), tenantId);

        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    @GetMapping("/tickets/unclaimed")
    public ResponseEntity<List<SupportTicketDto>> listUnclaimed(@PathVariable Long tenantId) {
        return ResponseEntity.ok(supportTicketService.listUnclaimed(tenantId));
    }

    @PostMapping("/tickets/{ticketId}/claim")
    public ResponseEntity<SupportTicketDto> claim(
            @PathVariable Long tenantId, @PathVariable Long ticketId) {
        return ResponseEntity.ok(supportTicketService.claim(currentUser(), ticketId));
    }

    @PostMapping("/tickets/{ticketId}/transfer")
    public ResponseEntity<SupportTicketDto> transfer(
            @PathVariable Long tenantId,
            @PathVariable Long ticketId,
            @Valid @RequestBody TransferTicketRequestDto request) {
        return ResponseEntity.ok(
                supportTicketService.transfer(currentUser(), ticketId, request.toStaffUserId()));
    }

    @PostMapping("/tickets/{ticketId}/close")
    public ResponseEntity<SupportTicketDto> close(
            @PathVariable Long tenantId, @PathVariable Long ticketId) {
        return ResponseEntity.ok(supportTicketService.close(currentUser(), ticketId));
    }

    @GetMapping("/members/{memberUserId}/channel")
    @AuditLog(
            action = "support.channel.view",
            resourceType = "ChatConversation",
            resourceIdExpression = "#memberUserId")
    public ResponseEntity<ChatConversationDetailDto> getChannel(
            @PathVariable Long tenantId, @PathVariable Long memberUserId) {
        Long channelId = requireChannelId(tenantId, memberUserId);

        return ResponseEntity.ok(chatConversationService.getConversation(currentUser(), channelId));
    }

    @GetMapping("/members/{memberUserId}/channel/messages")
    public ResponseEntity<ChatMessagePageDto> listChannelMessages(
            @PathVariable Long tenantId,
            @PathVariable Long memberUserId,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) Integer size) {
        Long channelId = requireChannelId(tenantId, memberUserId);

        return ResponseEntity.ok(
                chatConversationService.listMessages(
                        currentUser(), channelId, before, after, size));
    }

    @PostMapping("/members/{memberUserId}/channel/messages")
    @AuditLog(
            action = "support.channel.message.send",
            resourceType = "ChatConversation",
            resourceIdExpression = "#memberUserId")
    public ResponseEntity<ChatMessageDto> sendChannelMessage(
            @PathVariable Long tenantId,
            @PathVariable Long memberUserId,
            @Valid @RequestBody SendChatMessageRequestDto request) {
        Long channelId = requireChannelId(tenantId, memberUserId);
        ChatMessageDto message =
                chatConversationService.sendMessage(currentUser(), channelId, request.content());

        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    private Long requireChannelId(Long tenantId, Long memberUserId) {
        return supportTicketService
                .findChannel(tenantId, memberUserId)
                .map(ChatConversation::getId)
                .orElseThrow(ChatConversationNotFoundException::new);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }
}
