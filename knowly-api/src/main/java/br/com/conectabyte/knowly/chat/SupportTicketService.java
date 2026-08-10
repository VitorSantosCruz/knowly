package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.RequiresGlobalPermission;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.dto.SupportTicketDto;
import br.com.conectabyte.knowly.chat.exception.ChatAccessDeniedException;
import br.com.conectabyte.knowly.chat.exception.ChatConversationNotFoundException;
import br.com.conectabyte.knowly.chat.exception.SupportTicketConflictException;
import br.com.conectabyte.knowly.tenancy.BypassTenantFilterForOversight;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-9-16's ticket lifecycle. The channel (a SUPPORT-kind ChatConversation with exactly one
 * participant: the owning member) is lazily created here, get-or-create, the first time it's needed
 * -- never eagerly on membership creation.
 */
@Service
public class SupportTicketService {

    private final ChatConversationRepository chatConversationRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final GlobalPermissionService globalPermissionService;
    private final TenantMembershipRepository tenantMembershipRepository;

    public SupportTicketService(
            ChatConversationRepository chatConversationRepository,
            ChatParticipantRepository chatParticipantRepository,
            SupportTicketRepository supportTicketRepository,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            GlobalPermissionService globalPermissionService,
            TenantMembershipRepository tenantMembershipRepository) {
        this.chatConversationRepository = chatConversationRepository;
        this.chatParticipantRepository = chatParticipantRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.globalPermissionService = globalPermissionService;
        this.tenantMembershipRepository = tenantMembershipRepository;
    }

    @Transactional
    @AuditLog(action = "support.ticket.open", resourceType = "SupportTicket")
    public SupportTicketDto openTicket(User member, Long tenantId) {
        requireActiveMembership(member, tenantId);
        ChatConversation channel = getOrCreateChannel(member, tenantId);

        if (supportTicketRepository
                .findBySupportChannelIdAndStatusNot(channel.getId(), SupportTicketStatus.CLOSED)
                .isPresent()) {
            throw new SupportTicketConflictException("An open ticket already exists");
        }

        SupportTicket ticket = supportTicketRepository.save(new SupportTicket(channel));

        return SupportTicketDto.from(ticket);
    }

    private void requireActiveMembership(User member, Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        boolean active =
                tenantMembershipRepository
                        .findByUserAndTenant(member, tenant)
                        .filter(TenantMembership::isActive)
                        .isPresent();

        if (!active) {
            throw new TenantAccessDeniedException();
        }
    }

    ChatConversation getOrCreateChannel(User member, Long tenantId) {
        return chatConversationRepository
                .findByTenantIdAndOwnerIdAndKind(
                        tenantId, member.getId(), ChatConversationKind.SUPPORT)
                .orElseGet(
                        () -> {
                            Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
                            ChatConversation channel =
                                    chatConversationRepository.save(
                                            new ChatConversation(
                                                    ChatConversationKind.SUPPORT,
                                                    tenant,
                                                    null,
                                                    member));
                            chatParticipantRepository.save(new ChatParticipant(channel, member));
                            return channel;
                        });
    }

    /**
     * {@code @BypassTenantFilterForOversight}: this query already takes and filters by an explicit
     * {@code tenantId} parameter -- letting {@code TenantFilterAspect}'s ambient session-scoped
     * filter also apply ANDs it with the caller's *currently acting-as* tenant, which silently
     * returns nothing whenever a staff member is acting as tenant A but handling a support ticket
     * that belongs to tenant B (their own STAFF_SUPPORT_HANDLE permission is unconditional, but
     * this lookup never got that far). Found live (2026-08-04): claiming a ticket while acting as
     * an unrelated tenant left the whole channel permanently 404ing (CHAT_CONVERSATION_NOT_FOUND)
     * until the staff member cleared their active tenant.
     */
    @Transactional(readOnly = true)
    @BypassTenantFilterForOversight
    public java.util.Optional<ChatConversation> findChannel(Long tenantId, Long memberUserId) {
        return chatConversationRepository.findByTenantIdAndOwnerIdAndKind(
                tenantId, memberUserId, ChatConversationKind.SUPPORT);
    }

    /**
     * The channel's current non-CLOSED ticket, if any -- the only way for a client to learn a
     * ticket's status/assignee without having just performed the claim/transfer/close action that
     * returned it inline. Added (2026-08-04) after a live gap: a staff member who claimed a ticket
     * then reloaded the page (or followed a direct {@code /support/:channelId} link) had no way to
     * re-fetch that state, permanently losing the transfer/close controls for that session even
     * though the backend still considered them the assignee.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<SupportTicket> findActiveTicketForChannel(Long channelId) {
        return supportTicketRepository.findBySupportChannelIdAndStatusNot(
                channelId, SupportTicketStatus.CLOSED);
    }

    /**
     * Unified entity search (2026-08-10 amendment), REQ-21: the "does this caller have a reachable
     * Support channel" check backing {@code ChatEntitySearchService}'s Support-label match.
     * Composes two already-existing lookups -- {@link #findChannel}'s member-channel path (already
     * used by {@code SupportChannelController#requireChannelId}) and the same
     * unclaimed-inbox/claimed-ticket visibility already used by {@link #listUnclaimed}/{@link
     * #claim} -- into one read-only, side-effect-free method, rather than duplicating either query.
     * Not annotated with {@code @RequiresGlobalPermission} (unlike {@code listUnclaimed}): a plain
     * member has no {@code STAFF_SUPPORT_HANDLE} permission at all and must still be able to reach
     * their own channel.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<ChatConversation> findOwnOrClaimableChannel(
            User actor, Long activeTenantId) {
        if (activeTenantId == null) {
            return java.util.Optional.empty();
        }

        java.util.Optional<ChatConversation> ownChannel =
                findChannel(activeTenantId, actor.getId());
        if (ownChannel.isPresent()) {
            return ownChannel;
        }

        if (actor.getGlobalRole() == null
                || !globalPermissionService.hasPermission(
                        actor, GlobalPermission.STAFF_SUPPORT_HANDLE)) {
            return java.util.Optional.empty();
        }

        java.util.Optional<SupportTicket> claimed =
                supportTicketRepository.findByStatus(SupportTicketStatus.ASSIGNED).stream()
                        .filter(
                                ticket ->
                                        ticket.getAssignedStaff() != null
                                                && ticket.getAssignedStaff()
                                                        .getId()
                                                        .equals(actor.getId()))
                        .filter(ticket -> belongsToTenant(ticket, activeTenantId))
                        .findFirst();
        if (claimed.isPresent()) {
            return java.util.Optional.of(claimed.get().getSupportChannel());
        }

        return supportTicketRepository.findByStatus(SupportTicketStatus.OPEN).stream()
                .filter(ticket -> belongsToTenant(ticket, activeTenantId))
                .findFirst()
                .map(SupportTicket::getSupportChannel);
    }

    private boolean belongsToTenant(SupportTicket ticket, Long tenantId) {
        return ticket.getSupportChannel().getTenant() != null
                && ticket.getSupportChannel().getTenant().getId().equals(tenantId);
    }

    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.STAFF_SUPPORT_HANDLE)
    public List<SupportTicketDto> listUnclaimed(Long tenantId) {
        return supportTicketRepository.findByStatus(SupportTicketStatus.OPEN).stream()
                .filter(
                        ticket ->
                                ticket.getSupportChannel().getTenant() != null
                                        && ticket.getSupportChannel()
                                                .getTenant()
                                                .getId()
                                                .equals(tenantId))
                .map(SupportTicketDto::from)
                .toList();
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_SUPPORT_HANDLE)
    @AuditLog(
            action = "support.ticket.claim",
            resourceType = "SupportTicket",
            resourceIdExpression = "#ticketId")
    public SupportTicketDto claim(User staffUser, Long ticketId) {
        SupportTicket ticket =
                supportTicketRepository
                        .findById(ticketId)
                        .orElseThrow(ChatConversationNotFoundException::new);

        if (ticket.getStatus() != SupportTicketStatus.OPEN) {
            throw new SupportTicketConflictException("Ticket is already claimed or closed");
        }

        ticket.setStatus(SupportTicketStatus.ASSIGNED);
        ticket.setAssignedStaff(staffUser);

        return SupportTicketDto.from(supportTicketRepository.save(ticket));
    }

    @Transactional
    @RequiresGlobalPermission(GlobalPermission.STAFF_SUPPORT_HANDLE)
    @AuditLog(
            action = "support.ticket.transfer",
            resourceType = "SupportTicket",
            resourceIdExpression = "#ticketId")
    public SupportTicketDto transfer(User staffUser, Long ticketId, Long toStaffUserId) {
        SupportTicket ticket =
                supportTicketRepository
                        .findById(ticketId)
                        .orElseThrow(ChatConversationNotFoundException::new);

        if (ticket.getStatus() != SupportTicketStatus.ASSIGNED
                || ticket.getAssignedStaff() == null
                || !ticket.getAssignedStaff().getId().equals(staffUser.getId())) {
            throw new ChatAccessDeniedException();
        }

        User target =
                userRepository
                        .findById(toStaffUserId)
                        .orElseThrow(ChatConversationNotFoundException::new);
        if (target.getGlobalRole() == null
                || !globalPermissionService.hasPermission(
                        target, GlobalPermission.STAFF_SUPPORT_HANDLE)) {
            throw new IllegalArgumentException("Transfer target lacks STAFF_SUPPORT_HANDLE");
        }

        ticket.setAssignedStaff(target);

        return SupportTicketDto.from(supportTicketRepository.save(ticket));
    }

    @Transactional
    @AuditLog(
            action = "support.ticket.close",
            resourceType = "SupportTicket",
            resourceIdExpression = "#ticketId")
    public SupportTicketDto close(User staffUser, Long ticketId) {
        SupportTicket ticket =
                supportTicketRepository
                        .findById(ticketId)
                        .orElseThrow(ChatConversationNotFoundException::new);

        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw new SupportTicketConflictException("Ticket is already closed");
        }

        if (ticket.getAssignedStaff() == null
                || !ticket.getAssignedStaff().getId().equals(staffUser.getId())) {
            throw new ChatAccessDeniedException();
        }

        ticket.setStatus(SupportTicketStatus.CLOSED);
        ticket.setClosedAt(java.time.Instant.now());

        return SupportTicketDto.from(supportTicketRepository.save(ticket));
    }
}
