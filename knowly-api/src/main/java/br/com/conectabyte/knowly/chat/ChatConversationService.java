package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventWriter;
import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.dto.ChatAddParticipantsResultDto;
import br.com.conectabyte.knowly.chat.dto.ChatConversationDetailDto;
import br.com.conectabyte.knowly.chat.dto.ChatConversationSummaryDto;
import br.com.conectabyte.knowly.chat.dto.ChatDiscoverableGroupDto;
import br.com.conectabyte.knowly.chat.dto.ChatJoinRequestDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessageDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessagePageDto;
import br.com.conectabyte.knowly.chat.dto.ChatParticipantRejectionDto;
import br.com.conectabyte.knowly.chat.dto.CreateChatConversationRequestDto;
import br.com.conectabyte.knowly.chat.dto.CreateChatConversationRequestDto.ChatConversationRequestKind;
import br.com.conectabyte.knowly.chat.exception.ChatAccessDeniedException;
import br.com.conectabyte.knowly.chat.exception.ChatAdminAlreadyGrantedException;
import br.com.conectabyte.knowly.chat.exception.ChatConversationNotFoundException;
import br.com.conectabyte.knowly.chat.exception.ChatDuplicateParticipantException;
import br.com.conectabyte.knowly.chat.exception.ChatGroupStateConflictException;
import br.com.conectabyte.knowly.chat.exception.ChatGroupStateConflictException.Detail;
import br.com.conectabyte.knowly.chat.exception.ChatIneligibleParticipantException;
import br.com.conectabyte.knowly.chat.exception.ChatJoinRequestConflictException;
import br.com.conectabyte.knowly.chat.exception.ChatVisibilityUnchangedException;
import br.com.conectabyte.knowly.identity.UserProfile;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import br.com.conectabyte.knowly.softdelete.AllowDeletedForOversight;
import br.com.conectabyte.knowly.tenancy.BypassTenantFilterForOversight;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.PermissionService;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.dto.PageResponseDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatConversationService {

    private final ChatConversationRepository chatConversationRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatJoinRequestRepository chatJoinRequestRepository;
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
            ChatJoinRequestRepository chatJoinRequestRepository,
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
        this.chatJoinRequestRepository = chatJoinRequestRepository;
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
                    // logical-delete-everywhere (2026-08-04): a soft-deleted user's id must not be
                    // addable to a brand-new conversation, even if the client already has it (a
                    // stale bookmark, old conversation, browser autofill).
                    userRepository
                            .findByIdAndDeletedAtIsNull(targetId)
                            .orElseThrow(ChatConversationNotFoundException::new);
            tenantAnchor = chatEligibilityService.resolveDirectAnchor(actor, target);
        } else {
            kind = ChatConversationKind.PEER_GROUP;
            // Same bug class as ChatEligibilityService#resolveDirectAnchor (see 7565ee0): a
            // group's tenant anchor must be server-derived from the actor's active session
            // (TenantContext#getActiveTenantId(), populated by TenantContextFilter from the
            // session), never taken as-is from the client-supplied request.tenantId(). The
            // client value is, at best, a cross-check -- a mismatch is rejected outright rather
            // than silently overridden, so a stale/forged client value can never widen scope.
            Long activeTenantId = tenantContext.getActiveTenantId().orElse(null);
            if (request.tenantId() != null && !request.tenantId().equals(activeTenantId)) {
                throw new ChatAccessDeniedException();
            }
            tenantAnchor = activeTenantId;
            for (Long participantId : participantIds) {
                User participant =
                        userRepository
                                .findByIdAndDeletedAtIsNull(participantId)
                                .orElseThrow(ChatConversationNotFoundException::new);
                // Bug fix (2026-08-09): the actor/creator (always present in participantIds, see
                // above) is checked via isEligibleAsActor -- a staff actor acting under their own
                // active-session tenant is eligible for it even without a real TenantMembership row
                // (see ChatEligibilityService#isEligibleAsActor). Every other participant being
                // added still requires a real membership via the unweakened isEligible check.
                boolean eligible =
                        participant.getId().equals(actor.getId())
                                ? chatEligibilityService.isEligibleAsActor(
                                        participant, tenantAnchor)
                                : chatEligibilityService.isEligible(participant, tenantAnchor);
                if (!eligible) {
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
            ChatParticipant participantRow = new ChatParticipant(conversation, participant);
            // REQ-1: the creator of a new PEER_GROUP conversation is recorded as its first admin.
            if (kind == ChatConversationKind.PEER_GROUP && participantId.equals(actor.getId())) {
                participantRow.setAdmin(true);
            }
            chatParticipantRepository.save(participantRow);
        }

        return ChatConversationSummaryDto.from(conversation, List.copyOf(participantIds), null);
    }

    @Transactional(readOnly = true)
    public List<ChatConversationSummaryDto> listConversations(User actor) {
        List<ChatConversation> conversations =
                chatParticipantRepository.findByUserId(actor.getId()).stream()
                        .map(ChatParticipant::getConversation)
                        .filter(conversation -> isVisibleUnderActiveTenant(actor, conversation))
                        .toList();

        Map<Long, Instant> lastMessageAtByConversationId =
                conversations.isEmpty()
                        ? Map.of()
                        : chatMessageRepository
                                .findLastMessageAtByConversationIdIn(
                                        conversations.stream()
                                                .map(ChatConversation::getId)
                                                .toList())
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                ChatMessageRepository.ConversationLastMessageAt
                                                        ::getConversationId,
                                                ChatMessageRepository.ConversationLastMessageAt
                                                        ::getLastMessageAt));

        return conversations.stream()
                .map(
                        conversation ->
                                ChatConversationSummaryDto.from(
                                        conversation,
                                        participantIdsOf(conversation.getId()),
                                        lastMessageAtByConversationId.get(conversation.getId())))
                .toList();
    }

    /**
     * chat-unified-ui follow-up: staff can be a participant of both staff-only (null-tenant) 1:1
     * conversations and tenant-anchored ones, so unlike a regular tenant member -- who only ever
     * has conversations in their single home tenant -- {@code listConversations} must derive which
     * of those a staff actor should see from their currently active tenant ({@link
     * TenantContext#getActiveTenantId()}, session-derived, never client-supplied), mirroring the
     * anchor logic {@link ChatEligibilityService#eligibleAnchorsForActor} already applies to the
     * "Haven't talked yet" list. A non-staff actor's conversations are never filtered here -- they
     * can never hold a staff-only anchor in the first place.
     */
    private boolean isVisibleUnderActiveTenant(User actor, ChatConversation conversation) {
        if (actor.getGlobalRole() != GlobalRole.STAFF
                && actor.getGlobalRole() != GlobalRole.STAFF_ADMIN) {
            return true;
        }

        java.util.Optional<Long> activeTenantId = tenantContext.getActiveTenantId();
        if (activeTenantId.isPresent()) {
            return conversation.getTenant() != null
                    && activeTenantId.get().equals(conversation.getTenant().getId());
        }

        return conversation.getTenant() == null;
    }

    @Transactional(readOnly = true)
    public ChatConversationDetailDto getConversation(User actor, Long conversationId) {
        ChatConversation conversation = requireReadableConversation(actor, conversationId);
        List<Long> participantIds = participantIdsOf(conversation.getId());
        Map<Long, String> nicknames =
                participantIds.stream()
                        .collect(Collectors.toMap(id -> id, this::nicknameOfUserId, (a, b) -> a));
        List<Long> adminUserIds = adminUserIdsOf(conversation.getId());
        Map<Long, String> avatarUrls = avatarUrlsOf(participantIds);

        return ChatConversationDetailDto.from(
                conversation, participantIds, nicknames, adminUserIds, avatarUrls);
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

        // REQ-44/45/46: a distinct, new rule for *archived* groups -- deliberately not an
        // extension of the active-group STAFF_ADMIN/MEMBER_ADMIN look-in below (REQ-44 grants
        // plain STAFF, not just STAFF_ADMIN, for a tenant group; a MEMBER_ADMIN gets no residual
        // access to an archived group's history at all).
        if (conversation.getArchivedAt() != null) {
            if (conversation.getTenant() != null && actor.getGlobalRole() != null) {
                auditOversightView(actor, conversation);
                return conversation;
            }
            if (conversation.getTenant() == null && tenantContext.isStaffAdmin()) {
                auditOversightView(actor, conversation);
                return conversation;
            }
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

    private List<Long> adminUserIdsOf(Long conversationId) {
        return chatParticipantRepository.findByConversationIdAndAdminTrue(conversationId).stream()
                .map(p -> p.getUser().getId())
                .toList();
    }

    private Long tenantAnchorOf(ChatConversation conversation) {
        return conversation.getTenant() == null ? null : conversation.getTenant().getId();
    }

    /**
     * Builds the response DTO directly from an already-loaded, already-authorized conversation --
     * deliberately not a call to {@link #getConversation}, which re-derives *read* access from
     * scratch and would force every group-mutation test to also mock the read path.
     */
    private ChatConversationDetailDto buildDetailDto(ChatConversation conversation) {
        List<Long> participantIds = participantIdsOf(conversation.getId());
        Map<Long, String> nicknames =
                participantIds.stream()
                        .collect(Collectors.toMap(id -> id, this::nicknameOfUserId, (a, b) -> a));
        List<Long> adminUserIds = adminUserIdsOf(conversation.getId());
        Map<Long, String> avatarUrls = avatarUrlsOf(participantIds);

        return ChatConversationDetailDto.from(
                conversation, participantIds, nicknames, adminUserIds, avatarUrls);
    }

    // ---------------------------------------------------------------------------------------
    // chat-group-membership-management: group admin role, add/remove/leave, visibility,
    // discovery, join requests, direct join, deletion. See PLAN.md for the full rationale.
    // ---------------------------------------------------------------------------------------

    private ChatConversation loadConversation(Long conversationId) {
        return chatConversationRepository
                .findByIdRespectingFilter(conversationId)
                .or(() -> oversightConversationLoader.loadIgnoringTenantFilter(conversationId))
                .orElseThrow(ChatConversationNotFoundException::new);
    }

    private void requirePeerGroup(ChatConversation conversation) {
        if (conversation.getKind() != ChatConversationKind.PEER_GROUP) {
            throw new ChatGroupStateConflictException(Detail.NOT_PEER_GROUP);
        }
    }

    private void requireActiveGroup(ChatConversation conversation) {
        if (conversation.getArchivedAt() != null) {
            throw new ChatGroupStateConflictException(Detail.ARCHIVED);
        }
    }

    /**
     * REQ-6: re-derived at request time from {@code chat_participants.is_admin}, never cached,
     * never inferred from tenant/platform role -- see PLAN's "Architectural decisions".
     */
    private void requireGroupAdmin(User actor, ChatConversation conversation) {
        ChatParticipant participant =
                chatParticipantRepository
                        .findByConversationIdAndUserId(conversation.getId(), actor.getId())
                        .orElseThrow(ChatAccessDeniedException::new);
        if (!participant.isAdmin()) {
            throw new ChatAccessDeniedException();
        }
    }

    private boolean isGroupAdmin(User actor, Long conversationId) {
        return chatParticipantRepository
                .findByConversationIdAndUserId(conversationId, actor.getId())
                .map(ChatParticipant::isAdmin)
                .orElse(false);
    }

    @Transactional
    @AuditLog(
            action = "chat.group.admin_promote",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public ChatConversationDetailDto promoteToAdmin(
            User actor, Long conversationId, Long targetUserId) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);
        requireGroupAdmin(actor, conversation);

        ChatParticipant target =
                chatParticipantRepository
                        .findByConversationIdAndUserId(conversationId, targetUserId)
                        .orElseThrow(ChatConversationNotFoundException::new);
        if (target.isAdmin()) {
            throw new ChatAdminAlreadyGrantedException();
        }

        target.setAdmin(true);
        chatParticipantRepository.save(target);

        return buildDetailDto(conversation);
    }

    @Transactional
    @AuditLog(
            action = "chat.group.participant_add",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public ChatAddParticipantsResultDto addParticipants(
            User actor, Long conversationId, List<Long> userIds) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);
        requireActiveGroup(conversation);
        requireGroupAdmin(actor, conversation);

        Long tenantAnchor = tenantAnchorOf(conversation);
        List<ChatParticipantRejectionDto> rejected = new ArrayList<>();
        int addedCount = 0;

        for (Long userId : new LinkedHashSet<>(userIds)) {
            if (chatParticipantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
                rejected.add(
                        new ChatParticipantRejectionDto(
                                userId, ChatParticipantRejectionDto.Reason.ALREADY_PARTICIPANT));
                continue;
            }

            User candidate = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
            if (candidate == null || !chatEligibilityService.isEligible(candidate, tenantAnchor)) {
                rejected.add(
                        new ChatParticipantRejectionDto(
                                userId, ChatParticipantRejectionDto.Reason.INELIGIBLE));
                continue;
            }

            chatParticipantRepository.save(new ChatParticipant(conversation, candidate));
            addedCount++;
        }

        if (addedCount == 0 && !rejected.isEmpty()) {
            throw new ChatIneligibleParticipantException();
        }

        return new ChatAddParticipantsResultDto(buildDetailDto(conversation), rejected);
    }

    @Transactional
    @AuditLog(
            action = "chat.group.participant_remove",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public ChatConversationDetailDto removeParticipant(
            User actor, Long conversationId, Long targetUserId) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);
        requireActiveGroup(conversation);
        requireGroupAdmin(actor, conversation);

        ChatParticipant target =
                chatParticipantRepository
                        .findByConversationIdAndUserId(conversationId, targetUserId)
                        .orElseThrow(ChatConversationNotFoundException::new);

        if (chatParticipantRepository.countByConversationId(conversationId) <= 1) {
            throw new ChatGroupStateConflictException(Detail.WOULD_EMPTY_GROUP);
        }

        chatParticipantRepository.delete(target);
        handleAdminDepartureIfNeeded(conversationId);

        return buildDetailDto(conversation);
    }

    @Transactional
    @AuditLog(
            action = "chat.group.leave",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public void leaveConversation(User actor, Long conversationId) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);

        ChatParticipant own =
                chatParticipantRepository
                        .findByConversationIdAndUserId(conversationId, actor.getId())
                        .orElseThrow(ChatAccessDeniedException::new);

        chatParticipantRepository.delete(own);

        long remaining = chatParticipantRepository.countByConversationId(conversationId);
        if (remaining == 0) {
            archiveIfEmptied(conversation);
        } else {
            handleAdminDepartureIfNeeded(conversationId);
        }
    }

    /**
     * REQ-54: runs inside the same transaction as the triggering removal (REQ-7/13/18), so a group
     * is never observed with participants but zero admins.
     */
    private void handleAdminDepartureIfNeeded(Long conversationId) {
        long remainingParticipants =
                chatParticipantRepository.countByConversationId(conversationId);
        if (remainingParticipants == 0) {
            return;
        }

        long remainingAdmins =
                chatParticipantRepository.countByConversationIdAndAdminTrue(conversationId);
        if (remainingAdmins > 0) {
            return;
        }

        List<ChatParticipant> ordered =
                chatParticipantRepository.findRemainingOrderedBySeniority(conversationId);
        if (ordered.isEmpty()) {
            return;
        }

        ChatParticipant successor = ordered.get(0);
        successor.setAdmin(true);
        chatParticipantRepository.save(successor);

        ChatConversation conversation = successor.getConversation();
        auditEventWriter.write(
                new AuditEvent(
                        successor.getUser().getId(),
                        tenantAnchorOf(conversation),
                        "chat.group.admin_succession",
                        "ChatConversation",
                        String.valueOf(conversationId),
                        AuditOutcome.SUCCESS));
    }

    /** REQ-43/47: a leave-only transition (see PLAN's "Empty-group archival" decision). */
    private void archiveIfEmptied(ChatConversation conversation) {
        if (conversation.getVisibility() == ChatGroupVisibility.PUBLIC) {
            return;
        }

        conversation.setArchivedAt(Instant.now());
        chatConversationRepository.save(conversation);

        auditEventWriter.write(
                new AuditEvent(
                        null,
                        tenantAnchorOf(conversation),
                        "chat.group.archive",
                        "ChatConversation",
                        String.valueOf(conversation.getId()),
                        AuditOutcome.SUCCESS));
    }

    @Transactional
    @AuditLog(
            action = "chat.group.visibility_change",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public ChatConversationDetailDto changeVisibility(
            User actor, Long conversationId, ChatGroupVisibility newVisibility) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);
        requireActiveGroup(conversation);
        requireGroupAdmin(actor, conversation);

        if (conversation.getVisibility() == newVisibility) {
            throw new ChatVisibilityUnchangedException();
        }

        conversation.setVisibility(newVisibility);
        chatConversationRepository.save(conversation);

        return buildDetailDto(conversation);
    }

    @Transactional(readOnly = true)
    public PageResponseDto<ChatDiscoverableGroupDto> listDiscoverableGroups(
            User actor, Pageable pageable) {
        Page<ChatConversation> page = chatConversationRepository.findDiscoverable(pageable);

        List<ChatDiscoverableGroupDto> content =
                page.getContent().stream()
                        .filter(
                                conversation ->
                                        chatEligibilityService.isEligible(
                                                actor, tenantAnchorOf(conversation)))
                        .filter(conversation -> !isParticipant(actor, conversation))
                        .map(
                                conversation ->
                                        ChatDiscoverableGroupDto.from(
                                                conversation,
                                                chatParticipantRepository.countByConversationId(
                                                        conversation.getId())))
                        .toList();

        return new PageResponseDto<>(
                content, page.getNumber(), page.getSize(), content.size(), page.getTotalPages());
    }

    @Transactional
    @AuditLog(
            action = "chat.group.join_request_submit",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public ChatJoinRequestDto submitJoinRequest(User actor, Long conversationId) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);
        requireActiveGroup(conversation);

        if (conversation.getVisibility() != ChatGroupVisibility.REQUEST_TO_JOIN) {
            throw new ChatGroupStateConflictException(Detail.WRONG_VISIBILITY_MODE);
        }

        if (chatParticipantRepository.existsByConversationIdAndUserId(
                conversationId, actor.getId())) {
            throw new ChatDuplicateParticipantException();
        }

        if (!chatEligibilityService.isEligible(actor, tenantAnchorOf(conversation))) {
            throw new ChatIneligibleParticipantException();
        }

        if (chatJoinRequestRepository
                .findByConversationIdAndRequesterIdAndStatus(
                        conversationId, actor.getId(), ChatJoinRequestStatus.PENDING)
                .isPresent()) {
            throw new ChatJoinRequestConflictException(
                    ChatJoinRequestConflictException.Detail.DUPLICATE_PENDING);
        }

        ChatJoinRequest saved =
                chatJoinRequestRepository.save(new ChatJoinRequest(conversation, actor));

        return ChatJoinRequestDto.from(saved, nicknameOfUserId(actor.getId()));
    }

    @Transactional(readOnly = true)
    public List<ChatJoinRequestDto> listJoinRequests(
            User actor, Long conversationId, ChatJoinRequestStatus status) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);
        requireGroupAdmin(actor, conversation);

        ChatJoinRequestStatus effectiveStatus =
                status == null ? ChatJoinRequestStatus.PENDING : status;

        return chatJoinRequestRepository
                .findByConversationIdAndStatus(conversationId, effectiveStatus)
                .stream()
                .map(
                        request ->
                                ChatJoinRequestDto.from(
                                        request, nicknameOfUserId(request.getRequester().getId())))
                .toList();
    }

    private ChatJoinRequest loadJoinRequest(Long conversationId, Long requestId) {
        ChatJoinRequest request =
                chatJoinRequestRepository
                        .findById(requestId)
                        .orElseThrow(ChatConversationNotFoundException::new);
        if (!request.getConversation().getId().equals(conversationId)) {
            throw new ChatConversationNotFoundException();
        }
        return request;
    }

    @Transactional
    @AuditLog(
            action = "chat.group.join_request_approve",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public ChatJoinRequestDto approveJoinRequest(User actor, Long conversationId, Long requestId) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);
        requireGroupAdmin(actor, conversation);

        ChatJoinRequest request = loadJoinRequest(conversationId, requestId);
        if (request.getStatus() != ChatJoinRequestStatus.PENDING) {
            throw new ChatJoinRequestConflictException(
                    ChatJoinRequestConflictException.Detail.ALREADY_DECIDED);
        }

        // REQ-30a (AppSec-mandated): re-derive eligibility fresh, a second time, independent of
        // the submission-time check -- never trust the earlier snapshot. Leaves the request
        // PENDING (not auto-rejected) on failure, per REQ-30a's exact wording.
        if (!chatEligibilityService.isEligible(
                request.getRequester(), tenantAnchorOf(conversation))) {
            throw new ChatIneligibleParticipantException();
        }

        chatParticipantRepository.save(new ChatParticipant(conversation, request.getRequester()));
        request.setStatus(ChatJoinRequestStatus.APPROVED);
        request.setDecidedBy(actor);
        request.setDecidedAt(Instant.now());
        chatJoinRequestRepository.save(request);

        return ChatJoinRequestDto.from(request, nicknameOfUserId(request.getRequester().getId()));
    }

    @Transactional
    @AuditLog(
            action = "chat.group.join_request_reject",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public ChatJoinRequestDto rejectJoinRequest(User actor, Long conversationId, Long requestId) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);
        requireGroupAdmin(actor, conversation);

        ChatJoinRequest request = loadJoinRequest(conversationId, requestId);
        if (request.getStatus() != ChatJoinRequestStatus.PENDING) {
            throw new ChatJoinRequestConflictException(
                    ChatJoinRequestConflictException.Detail.ALREADY_DECIDED);
        }

        request.setStatus(ChatJoinRequestStatus.REJECTED);
        request.setDecidedBy(actor);
        request.setDecidedAt(Instant.now());
        chatJoinRequestRepository.save(request);

        return ChatJoinRequestDto.from(request, nicknameOfUserId(request.getRequester().getId()));
    }

    @Transactional
    @AuditLog(
            action = "chat.group.direct_join",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public ChatConversationDetailDto joinPublicGroup(User actor, Long conversationId) {
        ChatConversation conversation = loadConversation(conversationId);
        requirePeerGroup(conversation);

        if (conversation.getVisibility() != ChatGroupVisibility.PUBLIC) {
            throw new ChatGroupStateConflictException(Detail.WRONG_VISIBILITY_MODE);
        }

        if (chatParticipantRepository.existsByConversationIdAndUserId(
                conversationId, actor.getId())) {
            throw new ChatDuplicateParticipantException();
        }

        if (!chatEligibilityService.isEligible(actor, tenantAnchorOf(conversation))) {
            throw new ChatIneligibleParticipantException();
        }

        chatParticipantRepository.save(new ChatParticipant(conversation, actor));

        return buildDetailDto(conversation);
    }

    private boolean hasChatGroupDeletePermission(User actor, Tenant tenant) {
        return tenantMembershipRepository
                .findByUserAndTenant(actor, tenant)
                .filter(TenantMembership::isActive)
                .filter(
                        membership ->
                                permissionService.hasPermission(
                                        membership, Permission.CHAT_GROUP_DELETE))
                .isPresent();
    }

    /**
     * REQ-48's four authorization paths, tried in this order (first match wins): (a) STAFF_ADMIN
     * unconditionally; (b) active MEMBER_ADMIN of the group's own tenant; (c) an active tenant
     * membership holding CHAT_GROUP_DELETE in the group's own tenant; (d) a current group admin of
     * this specific conversation.
     * {@code @AllowDeletedForOversight}/{@code @BypassTenantFilterForOversight} let this method see
     * an already-deleted or staff-only-tenant row long enough to distinguish REQ-51 (not-found)
     * from REQ-53 (already-deleted) -- the authorization check below still fully re-derives the
     * caller's rights independently.
     */
    @Transactional
    @AllowDeletedForOversight
    @BypassTenantFilterForOversight
    @AuditLog(
            action = "chat.group.delete",
            resourceType = "ChatConversation",
            resourceIdExpression = "#conversationId")
    public void deleteConversation(User actor, Long conversationId) {
        ChatConversation conversation =
                chatConversationRepository
                        .findByIdRespectingFilter(conversationId)
                        .orElseThrow(ChatConversationNotFoundException::new);

        if (conversation.getKind() != ChatConversationKind.PEER_GROUP) {
            throw new ChatGroupStateConflictException(Detail.NOT_PEER_GROUP);
        }

        if (conversation.getDeletedAt() != null) {
            throw new ChatGroupStateConflictException(Detail.ALREADY_DELETED);
        }

        boolean authorized =
                tenantContext.isStaffAdmin()
                        || (conversation.getTenant() != null
                                && isActiveMemberAdminOf(actor, conversation.getTenant()))
                        || (conversation.getTenant() != null
                                && hasChatGroupDeletePermission(actor, conversation.getTenant()))
                        || isGroupAdmin(actor, conversation.getId());

        if (!authorized) {
            throw new ChatAccessDeniedException();
        }

        Instant now = Instant.now();
        conversation.setDeletedAt(now);
        chatConversationRepository.save(conversation);
        chatParticipantRepository.softDeleteAllByConversationId(conversationId, now);
        chatMessageRepository.softDeleteAllByConversationId(conversationId, now);
    }

    private String nicknameOfUserId(Long userId) {
        return userProfileRepository
                .findById(userId)
                .map(UserProfile::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElseGet(() -> userRepository.findById(userId).map(User::getEmail).orElse(null));
    }

    // chat-unified-ui follow-up: same avatarUrl source/logic already used by
    // ChatEligibilityService#listCandidates for CandidateUserDto -- reused here, per participant,
    // so a DIRECT conversation's header can show the remote peer's photo. No new storage/URL
    // mechanism.
    private Map<Long, String> avatarUrlsOf(List<Long> participantIds) {
        // Deliberately not Collectors.toMap: its merge-based implementation throws NPE on a null
        // value, and a participant legitimately having no avatar (null) must not blow up here.
        Map<Long, String> avatarUrls = new java.util.HashMap<>();
        for (Long id : participantIds) {
            avatarUrls.put(
                    id,
                    userProfileRepository.findById(id).map(UserProfile::getAvatarUrl).orElse(null));
        }
        return avatarUrls;
    }

    private static List<ChatMessage> reversed(List<ChatMessage> descending) {
        List<ChatMessage> copy = new java.util.ArrayList<>(descending);
        java.util.Collections.reverse(copy);
        return copy;
    }
}
