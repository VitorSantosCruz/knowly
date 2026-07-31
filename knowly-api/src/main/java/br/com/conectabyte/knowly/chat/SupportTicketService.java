package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.RequiresGlobalPermission;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.dto.SupportTicketDto;
import br.com.conectabyte.knowly.chat.exception.ChatAccessDeniedException;
import br.com.conectabyte.knowly.chat.exception.ChatConversationNotFoundException;
import br.com.conectabyte.knowly.chat.exception.SupportTicketConflictException;
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

    @Transactional(readOnly = true)
    public java.util.Optional<ChatConversation> findChannel(Long tenantId, Long memberUserId) {
        return chatConversationRepository.findByTenantIdAndOwnerIdAndKind(
                tenantId, memberUserId, ChatConversationKind.SUPPORT);
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
