package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.auth.exception.AuthenticatedUserNotFoundException;
import br.com.conectabyte.knowly.chat.dto.AddChatParticipantsRequestDto;
import br.com.conectabyte.knowly.chat.dto.CandidateUserDto;
import br.com.conectabyte.knowly.chat.dto.ChangeChatVisibilityRequestDto;
import br.com.conectabyte.knowly.chat.dto.ChatAddParticipantsResultDto;
import br.com.conectabyte.knowly.chat.dto.ChatConversationDetailDto;
import br.com.conectabyte.knowly.chat.dto.ChatConversationSummaryDto;
import br.com.conectabyte.knowly.chat.dto.ChatDiscoverableGroupDto;
import br.com.conectabyte.knowly.chat.dto.ChatJoinRequestDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessageDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessagePageDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessageSearchPageDto;
import br.com.conectabyte.knowly.chat.dto.CreateChatConversationRequestDto;
import br.com.conectabyte.knowly.chat.dto.RenameChatConversationRequestDto;
import br.com.conectabyte.knowly.chat.dto.SendChatMessageRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.PageResponseDto;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatConversationService chatConversationService;
    private final ChatEligibilityService chatEligibilityService;
    private final ChatMessageSearchService chatMessageSearchService;
    private final ChatEntitySearchService chatEntitySearchService;
    private final UserRepository userRepository;

    public ChatController(
            ChatConversationService chatConversationService,
            ChatEligibilityService chatEligibilityService,
            ChatMessageSearchService chatMessageSearchService,
            ChatEntitySearchService chatEntitySearchService,
            UserRepository userRepository) {
        this.chatConversationService = chatConversationService;
        this.chatEligibilityService = chatEligibilityService;
        this.chatMessageSearchService = chatMessageSearchService;
        this.chatEntitySearchService = chatEntitySearchService;
        this.userRepository = userRepository;
    }

    /**
     * Unified entity search (2026-08-10 amendment), REQ-16 through REQ-26: separate from {@link
     * #searchMessages}, matches people/groups/Support/RAG conversations by name/title, or (blank
     * {@code q}) returns "recent places". No {@code @AuditLog} -- same reasoning as {@code
     * searchMessages}. Response shape is polymorphic by design (see PLAN's API contracts): {@code
     * ChatEntitySearchResponseDto} for a non-blank {@code q}, {@code ChatEntitySearchResultDto} for
     * a blank one, or a single {@code ChatEntitySearchSectionDto} for the {@code type}+{@code
     * offset} "see more" expand form.
     */
    @GetMapping("/search")
    public ResponseEntity<Object> searchEntities(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer offset,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return ResponseEntity.ok(
                chatEntitySearchService.search(currentUser(), q, type, offset, acceptLanguage));
    }

    /**
     * REQ-1 through REQ-15: no {@code @AuditLog} -- search is a read, not a state change, per
     * PLAN.md's "Architectural decisions" (structured logging happens inside {@link
     * ChatMessageSearchService} instead). No 403/404 for an inaccessible {@code conversationId}
     * filter (REQ-3) -- that path returns a normal 200 with an empty/short page.
     */
    @GetMapping("/messages/search")
    public ResponseEntity<ChatMessageSearchPageDto> searchMessages(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long senderId,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return ResponseEntity.ok(
                chatMessageSearchService.search(
                        currentUser(),
                        q,
                        senderId,
                        conversationId,
                        dateFrom,
                        dateTo,
                        cursor,
                        size,
                        acceptLanguage));
    }

    @PostMapping("/conversations")
    @AuditLog(action = "chat.conversation.create", resourceType = "ChatConversation")
    public ResponseEntity<ChatConversationSummaryDto> createConversation(
            @Valid @RequestBody CreateChatConversationRequestDto request) {
        ChatConversationSummaryDto conversation =
                chatConversationService.createConversation(currentUser(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversationSummaryDto>> listConversations() {
        return ResponseEntity.ok(chatConversationService.listConversations(currentUser()));
    }

    @GetMapping("/conversations/{id}")
    @AuditLog(
            action = "chat.conversation.view",
            resourceType = "ChatConversation",
            resourceIdExpression = "#id")
    public ResponseEntity<ChatConversationDetailDto> getConversation(@PathVariable Long id) {
        return ResponseEntity.ok(chatConversationService.getConversation(currentUser(), id));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ChatMessagePageDto> listMessages(
            @PathVariable Long id,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(
                chatConversationService.listMessages(currentUser(), id, before, after, size));
    }

    @PostMapping("/conversations/{id}/messages")
    @AuditLog(
            action = "chat.message.send",
            resourceType = "ChatConversation",
            resourceIdExpression = "#id")
    public ResponseEntity<ChatMessageDto> sendMessage(
            @PathVariable Long id, @Valid @RequestBody SendChatMessageRequestDto request) {
        ChatMessageDto message =
                chatConversationService.sendMessage(currentUser(), id, request.content());

        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @GetMapping("/eligible-participants")
    public ResponseEntity<List<CandidateUserDto>> listEligibleParticipants(
            @RequestParam String scope, @RequestParam(required = false) Long tenantId) {
        return ResponseEntity.ok(
                chatEligibilityService.listCandidates(currentUser(), scope, tenantId));
    }

    @PostMapping("/conversations/{id}/participants")
    public ResponseEntity<ChatAddParticipantsResultDto> addParticipants(
            @PathVariable Long id, @Valid @RequestBody AddChatParticipantsRequestDto request) {
        return ResponseEntity.ok(
                chatConversationService.addParticipants(currentUser(), id, request.userIds()));
    }

    @DeleteMapping("/conversations/{id}/participants/{userId}")
    public ResponseEntity<ChatConversationDetailDto> removeParticipant(
            @PathVariable Long id, @PathVariable Long userId) {
        return ResponseEntity.ok(
                chatConversationService.removeParticipant(currentUser(), id, userId));
    }

    @PostMapping("/conversations/{id}/leave")
    public ResponseEntity<Void> leaveGroup(@PathVariable Long id) {
        chatConversationService.leaveConversation(currentUser(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/admins/{userId}")
    public ResponseEntity<ChatConversationDetailDto> promoteToAdmin(
            @PathVariable Long id, @PathVariable Long userId) {
        return ResponseEntity.ok(chatConversationService.promoteToAdmin(currentUser(), id, userId));
    }

    @PutMapping("/conversations/{id}")
    @AuditLog(
            action = "chat.group.rename",
            resourceType = "ChatConversation",
            resourceIdExpression = "#id")
    public ResponseEntity<ChatConversationDetailDto> renameConversation(
            @PathVariable Long id, @Valid @RequestBody RenameChatConversationRequestDto request) {
        return ResponseEntity.ok(
                chatConversationService.renameConversation(
                        currentUser(), id, request.title(), request.icon()));
    }

    @PutMapping("/conversations/{id}/visibility")
    public ResponseEntity<ChatConversationDetailDto> changeVisibility(
            @PathVariable Long id, @Valid @RequestBody ChangeChatVisibilityRequestDto request) {
        return ResponseEntity.ok(
                chatConversationService.changeVisibility(currentUser(), id, request.visibility()));
    }

    @GetMapping("/discoverable-groups")
    public ResponseEntity<PageResponseDto<ChatDiscoverableGroupDto>> listDiscoverableGroups(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ResponseEntity.ok(
                chatConversationService.listDiscoverableGroups(
                        currentUser(), PageRequest.of(page, size)));
    }

    @PostMapping("/conversations/{id}/join-requests")
    public ResponseEntity<ChatJoinRequestDto> submitJoinRequest(@PathVariable Long id) {
        ChatJoinRequestDto request = chatConversationService.submitJoinRequest(currentUser(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping("/conversations/{id}/join-requests")
    public ResponseEntity<List<ChatJoinRequestDto>> listJoinRequests(
            @PathVariable Long id, @RequestParam(required = false) ChatJoinRequestStatus status) {
        return ResponseEntity.ok(
                chatConversationService.listJoinRequests(currentUser(), id, status));
    }

    @PostMapping("/conversations/{id}/join-requests/{requestId}/approve")
    public ResponseEntity<ChatJoinRequestDto> approveJoinRequest(
            @PathVariable Long id, @PathVariable Long requestId) {
        return ResponseEntity.ok(
                chatConversationService.approveJoinRequest(currentUser(), id, requestId));
    }

    @PostMapping("/conversations/{id}/join-requests/{requestId}/reject")
    public ResponseEntity<ChatJoinRequestDto> rejectJoinRequest(
            @PathVariable Long id, @PathVariable Long requestId) {
        return ResponseEntity.ok(
                chatConversationService.rejectJoinRequest(currentUser(), id, requestId));
    }

    @PostMapping("/conversations/{id}/join")
    public ResponseEntity<ChatConversationDetailDto> joinPublicGroup(@PathVariable Long id) {
        return ResponseEntity.ok(chatConversationService.joinPublicGroup(currentUser(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id) {
        chatConversationService.deleteConversation(currentUser(), id);
        return ResponseEntity.noContent().build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }
}
