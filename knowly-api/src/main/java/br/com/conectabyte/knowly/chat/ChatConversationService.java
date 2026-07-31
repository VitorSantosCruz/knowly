package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventWriter;
import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.dto.ChatConversationDetailDto;
import br.com.conectabyte.knowly.chat.dto.ChatConversationSummaryDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessageDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessagePageDto;
import br.com.conectabyte.knowly.chat.dto.CreateChatConversationRequestDto;
import br.com.conectabyte.knowly.chat.dto.CreateChatConversationRequestDto.ChatConversationRequestKind;
import br.com.conectabyte.knowly.chat.exception.ChatAccessDeniedException;
import br.com.conectabyte.knowly.chat.exception.ChatConversationNotFoundException;
import br.com.conectabyte.knowly.chat.exception.ChatIneligibleParticipantException;
import br.com.conectabyte.knowly.identity.UserProfile;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.PermissionService;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatConversationService {

    private final ChatConversationRepository chatConversationRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final ChatEligibilityService chatEligibilityService;
    private final ChatOversightConversationLoader oversightConversationLoader;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final PermissionService permissionService;
    private final GlobalPermissionService globalPermissionService;
    private final TenantContext tenantContext;
    private final AuditEventWriter auditEventWriter;

    public ChatConversationService(
            ChatConversationRepository chatConversationRepository,
            ChatParticipantRepository chatParticipantRepository,
            ChatMessageRepository chatMessageRepository,
            SupportTicketRepository supportTicketRepository,
            ChatEligibilityService chatEligibilityService,
            ChatOversightConversationLoader oversightConversationLoader,
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            TenantRepository tenantRepository,
            TenantMembershipRepository tenantMembershipRepository,
            PermissionService permissionService,
            GlobalPermissionService globalPermissionService,
            TenantContext tenantContext,
            AuditEventWriter auditEventWriter) {
        this.chatConversationRepository = chatConversationRepository;
        this.chatParticipantRepository = chatParticipantRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.chatEligibilityService = chatEligibilityService;
        this.oversightConversationLoader = oversightConversationLoader;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.tenantRepository = tenantRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.permissionService = permissionService;
        this.globalPermissionService = globalPermissionService;
        this.tenantContext = tenantContext;
        this.auditEventWriter = auditEventWriter;
    }

    @Transactional
    @AuditLog(action = "chat.conversation.create", resourceType = "ChatConversation")
    public ChatConversationSummaryDto createConversation(
            User actor, CreateChatConversationRequestDto request) {
        Set<Long> participantIds = new LinkedHashSet<>(request.participantUserIds());
        participantIds.add(actor.getId());

        Long tenantAnchor;
        ChatConversationKind kind;

        if (request.kind() == ChatConversationRequestKind.DIRECT) {
            kind = ChatConversationKind.PEER_DIRECT;
            if (participantIds.size() != 2) {
                throw new ChatIneligibleParticipantException();
            }
            Long targetId =
                    participantIds.stream()
                            .filter(id -> !id.equals(actor.getId()))
                            .findFirst()
                            .get();
            User target =
                    userRepository
                            .findById(targetId)
                            .orElseThrow(ChatConversationNotFoundException::new);
            tenantAnchor = chatEligibilityService.resolveDirectAnchor(actor, target);
        } else {
            kind = ChatConversationKind.PEER_GROUP;
            tenantAnchor = request.tenantId();
            for (Long participantId : participantIds) {
                User participant =
                        userRepository
                                .findById(participantId)
                                .orElseThrow(ChatConversationNotFoundException::new);
                if (!chatEligibilityService.isEligible(participant, tenantAnchor)) {
                    throw new ChatIneligibleParticipantException();
                }
            }
        }

        Tenant tenant =
                tenantAnchor == null ? null : tenantRepository.findById(tenantAnchor).orElse(null);
        ChatConversation conversation =
                chatConversationRepository.save(
                        new ChatConversation(kind, tenant, request.title(), null));

        for (Long participantId : participantIds) {
            User participant = userRepository.findById(participantId).orElseThrow();
            chatParticipantRepository.save(new ChatParticipant(conversation, participant));
        }

        return ChatConversationSummaryDto.from(conversation, List.copyOf(participantIds));
    }

    @Transactional(readOnly = true)
    public List<ChatConversationSummaryDto> listConversations(User actor) {
        return chatParticipantRepository.findByUserId(actor.getId()).stream()
                .map(ChatParticipant::getConversation)
                .map(
                        conversation ->
                                ChatConversationSummaryDto.from(
                                        conversation, participantIdsOf(conversation.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatConversationDetailDto getConversation(User actor, Long conversationId) {
        ChatConversation conversation = requireReadableConversation(actor, conversationId);
        List<Long> participantIds = participantIdsOf(conversation.getId());
        Map<Long, String> nicknames =
                participantIds.stream()
                        .collect(Collectors.toMap(id -> id, this::nicknameOfUserId, (a, b) -> a));

        return ChatConversationDetailDto.from(conversation, participantIds, nicknames);
    }

    @Transactional(readOnly = true)
    public ChatMessagePageDto listMessages(
            User actor, Long conversationId, String before, String after, Integer size) {
        requireReadableConversation(actor, conversationId);
        int pageSize = ChatCursor.clampSize(size);

        List<ChatMessage> ascendingMessages;
        String nextCursor;

        if (after != null) {
            long cursor = ChatCursor.decode(after);
            List<ChatMessage> page =
                    chatMessageRepository.findAfterCursor(
                            conversationId, cursor, PageRequest.of(0, pageSize));
            ascendingMessages = page;
            nextCursor =
                    page.isEmpty() ? null : ChatCursor.encode(page.get(page.size() - 1).getId());
        } else if (before != null) {
            long cursor = ChatCursor.decode(before);
            List<ChatMessage> descendingPage =
                    chatMessageRepository.findBeforeCursor(
                            conversationId, cursor, PageRequest.of(0, pageSize));
            ascendingMessages = reversed(descendingPage);
            nextCursor =
                    descendingPage.isEmpty()
                            ? null
                            : ChatCursor.encode(
                                    descendingPage.get(descendingPage.size() - 1).getId());
        } else {
            List<ChatMessage> descendingPage =
                    chatMessageRepository.findNewestFirst(
                            conversationId, PageRequest.of(0, pageSize));
            ascendingMessages = reversed(descendingPage);
            nextCursor =
                    descendingPage.size() < pageSize
                            ? null
                            : ChatCursor.encode(
                                    descendingPage.get(descendingPage.size() - 1).getId());
        }

        List<ChatMessageDto> messages =
                ascendingMessages.stream()
                        .map(m -> ChatMessageDto.from(m, nicknameOfUserId(m.getSender().getId())))
                        .toList();

        return new ChatMessagePageDto(messages, nextCursor);
    }

    @Transactional
    @AuditLog(
            action = "chat.message.send",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public ChatMessageDto sendMessage(User actor, Long conversationId, String content) {
        // Falls back to the filter-bypassing loader when the normal filtered lookup misses (e.g. a
        // staff participant of a NULL-tenant staff-only conversation who currently has an active
        // tenant selected) -- this never widens *authorization*, only which row can be found at
        // all; the participant/support-rights checks below are unaffected either way.
        ChatConversation conversation =
                chatConversationRepository
                        .findByIdRespectingFilter(conversationId)
                        .or(
                                () ->
                                        oversightConversationLoader.loadIgnoringTenantFilter(
                                                conversationId))
                        .orElseThrow(ChatConversationNotFoundException::new);

        if (conversation.getKind() == ChatConversationKind.SUPPORT) {
            requireSupportSendRights(actor, conversation);
        } else if (!chatParticipantRepository.existsByConversationIdAndUserId(
                conversationId, actor.getId())) {
            throw new ChatAccessDeniedException();
        }

        ChatMessage message =
                chatMessageRepository.save(new ChatMessage(conversation, actor, content));

        return ChatMessageDto.from(message, nicknameOfUserId(actor.getId()));
    }

    private void requireSupportSendRights(User actor, ChatConversation channel) {
        SupportTicket ticket =
                supportTicketRepository
                        .findBySupportChannelIdAndStatusNot(
                                channel.getId(), SupportTicketStatus.CLOSED)
                        .orElse(null);

        if (ticket == null) {
            throw new ChatAccessDeniedException();
        }

        boolean isOwner =
                channel.getOwner() != null && channel.getOwner().getId().equals(actor.getId());
        if (isOwner) {
            return;
        }

        boolean isAssignedStaff =
                ticket.getStatus() == SupportTicketStatus.ASSIGNED
                        && ticket.getAssignedStaff() != null
                        && ticket.getAssignedStaff().getId().equals(actor.getId());
        if (isAssignedStaff) {
            return;
        }

        throw new ChatAccessDeniedException();
    }

    /**
     * Resolves read access for both peer conversations and support channels: (1) genuine
     * chat_participants row, (2) REQ-5a/5b admin look-in for PEER_GROUP, (3) SUPPORT-kind read
     * rules (owning member / STAFF_SUPPORT_HANDLE staff / SUPPORT_CHANNEL_VIEW tenant members).
     */
    ChatConversation requireReadableConversation(User actor, Long conversationId) {
        var filtered = chatConversationRepository.findByIdRespectingFilter(conversationId);

        if (filtered.isPresent() && isParticipant(actor, filtered.get())) {
            return filtered.get();
        }

        if (filtered.isPresent() && filtered.get().getKind() == ChatConversationKind.SUPPORT) {
            if (canReadSupportChannel(actor, filtered.get())) {
                return filtered.get();
            }
        }

        ChatConversation conversation =
                oversightConversationLoader
                        .loadIgnoringTenantFilter(conversationId)
                        .orElseThrow(ChatConversationNotFoundException::new);

        if (isParticipant(actor, conversation)) {
            return conversation;
        }

        if (conversation.getKind() == ChatConversationKind.SUPPORT) {
            if (canReadSupportChannel(actor, conversation)) {
                return conversation;
            }
            throw new ChatAccessDeniedException();
        }

        if (conversation.getKind() != ChatConversationKind.PEER_GROUP) {
            // REQ-2: 1:1 conversations never get an admin override, of any kind.
            throw new ChatAccessDeniedException();
        }

        if (tenantContext.isStaffAdmin()) {
            auditOversightView(actor, conversation);
            return conversation;
        }

        if (conversation.getTenant() != null
                && isActiveMemberAdminOf(actor, conversation.getTenant())) {
            auditOversightView(actor, conversation);
            return conversation;
        }

        throw new ChatAccessDeniedException();
    }

    /**
     * Written directly via {@link AuditEventWriter} rather than {@code @AuditLog} -- this call
     * happens via self-invocation from within {@link #requireReadableConversation}, which would
     * silently bypass AuditLogAspect's annotation-driven advice (same same-class-call limitation
     * {@link ChatOversightConversationLoader} is split out to avoid). Still a distinct action
     * string ({@code chat.group.oversight_view}) from the normal {@code chat.conversation.view} a
     * genuine participant triggers.
     */
    private void auditOversightView(User actor, ChatConversation conversation) {
        auditEventWriter.write(
                new AuditEvent(
                        actor.getId(),
                        conversation.getTenant() == null ? null : conversation.getTenant().getId(),
                        "chat.group.oversight_view",
                        "ChatConversation",
                        String.valueOf(conversation.getId()),
                        AuditOutcome.SUCCESS));
    }

    private boolean canReadSupportChannel(User actor, ChatConversation channel) {
        boolean isOwner =
                channel.getOwner() != null && channel.getOwner().getId().equals(actor.getId());
        if (isOwner) {
            return true;
        }

        if (tenantContext.isStaffAdmin()) {
            return true;
        }

        if (actor.getGlobalRole() != null
                && globalPermissionService.hasPermission(
                        actor, GlobalPermission.STAFF_SUPPORT_HANDLE)) {
            return true;
        }

        if (channel.getTenant() != null) {
            var membership =
                    tenantMembershipRepository.findByUserAndTenant(actor, channel.getTenant());
            if (membership.isPresent() && membership.get().isActive()) {
                if (membership.get().getRole() == MembershipRole.MEMBER_ADMIN) {
                    return true;
                }
                return permissionService.hasPermission(
                        membership.get(), Permission.SUPPORT_CHANNEL_VIEW);
            }
        }

        return false;
    }

    private boolean isParticipant(User actor, ChatConversation conversation) {
        return chatParticipantRepository.existsByConversationIdAndUserId(
                conversation.getId(), actor.getId());
    }

    private boolean isActiveMemberAdminOf(User actor, Tenant tenant) {
        return tenantMembershipRepository
                .findByUserAndTenant(actor, tenant)
                .filter(TenantMembership::isActive)
                .filter(m -> m.getRole() == MembershipRole.MEMBER_ADMIN)
                .isPresent();
    }

    private List<Long> participantIdsOf(Long conversationId) {
        return chatParticipantRepository.findByConversationId(conversationId).stream()
                .map(p -> p.getUser().getId())
                .toList();
    }

    private String nicknameOfUserId(Long userId) {
        return userProfileRepository
                .findById(userId)
                .map(UserProfile::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElseGet(() -> userRepository.findById(userId).map(User::getEmail).orElse(null));
    }

    private static List<ChatMessage> reversed(List<ChatMessage> descending) {
        List<ChatMessage> copy = new java.util.ArrayList<>(descending);
        java.util.Collections.reverse(copy);
        return copy;
    }
}
