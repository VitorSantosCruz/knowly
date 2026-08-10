package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.exception.ChatAccessDeniedException;
import br.com.conectabyte.knowly.chat.exception.SupportTicketConflictException;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock private ChatConversationRepository chatConversationRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private SupportTicketRepository supportTicketRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private GlobalPermissionService globalPermissionService;
    @Mock private TenantMembershipRepository tenantMembershipRepository;

    private SupportTicketService service;

    @BeforeEach
    void setUp() {
        service =
                new SupportTicketService(
                        chatConversationRepository,
                        chatParticipantRepository,
                        supportTicketRepository,
                        tenantRepository,
                        userRepository,
                        globalPermissionService,
                        tenantMembershipRepository);
    }

    private User user(long id, String email) {
        User user = new User(email);
        user.setId(id);
        return user;
    }

    private ChatConversation channel(long id) {
        ChatConversation channel = new ChatConversation();
        channel.setId(id);
        return channel;
    }

    @Test
    void openTicketRejectsANonMember() {
        User member = user(1L, "member@example.com");
        when(tenantMembershipRepository.findByUserAndTenant(
                        org.mockito.ArgumentMatchers.eq(member),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openTicket(member, 10L))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException
                                .class);
    }

    @Test
    void claimingAnAlreadyAssignedTicketConflicts() {
        SupportTicket ticket = new SupportTicket(channel(1L));
        ticket.setId(1L);
        ticket.setStatus(SupportTicketStatus.ASSIGNED);
        ticket.setAssignedStaff(user(2L, "assignee@example.com"));
        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        User staffUser = user(3L, "staff@example.com");
        assertThatThrownBy(() -> service.claim(staffUser, 1L))
                .isInstanceOf(SupportTicketConflictException.class);
    }

    @Test
    void transferByANonAssigneeIsRejected() {
        SupportTicket ticket = new SupportTicket(channel(1L));
        ticket.setId(1L);
        ticket.setStatus(SupportTicketStatus.ASSIGNED);
        ticket.setAssignedStaff(user(2L, "assignee@example.com"));
        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        User notAssignee = user(3L, "not-assignee@example.com");
        assertThatThrownBy(() -> service.transfer(notAssignee, 1L, 4L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void closingAnAlreadyClosedTicketConflicts() {
        SupportTicket ticket = new SupportTicket(channel(1L));
        ticket.setId(1L);
        ticket.setStatus(SupportTicketStatus.CLOSED);
        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        User staffUser = user(2L, "staff@example.com");
        assertThatThrownBy(() -> service.close(staffUser, 1L))
                .isInstanceOf(SupportTicketConflictException.class);
    }

    @Test
    void closingByANonAssigneeIsRejected() {
        SupportTicket ticket = new SupportTicket(channel(1L));
        ticket.setId(1L);
        ticket.setStatus(SupportTicketStatus.ASSIGNED);
        ticket.setAssignedStaff(user(2L, "assignee@example.com"));
        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        User notAssignee = user(3L, "not-assignee@example.com");
        assertThatThrownBy(() -> service.close(notAssignee, 1L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    // --- Unified entity search (2026-08-10 amendment): findOwnOrClaimableChannel ---

    private ChatConversation channelForTenant(
            long id, br.com.conectabyte.knowly.tenancy.Tenant tenant) {
        ChatConversation channel = channel(id);
        channel.setTenant(tenant);
        return channel;
    }

    @Test
    void findOwnOrClaimableChannelReturnsTheMembersOwnOpenChannelWhenOneExists() {
        User member = user(1L, "member@example.com");
        ChatConversation ownChannel = channel(100L);
        when(chatConversationRepository.findByTenantIdAndOwnerIdAndKind(
                        10L, 1L, ChatConversationKind.SUPPORT))
                .thenReturn(Optional.of(ownChannel));

        var result = service.findOwnOrClaimableChannel(member, 10L);

        org.assertj.core.api.Assertions.assertThat(result).contains(ownChannel);
    }

    @Test
    void
            findOwnOrClaimableChannelReturnsNothingForAStaffCallerWithNoSupportPermissionAndNoClaimedTicket() {
        User staff = user(2L, "staff@example.com");
        staff.setGlobalRole(br.com.conectabyte.knowly.tenancy.GlobalRole.STAFF);
        when(chatConversationRepository.findByTenantIdAndOwnerIdAndKind(
                        10L, 2L, ChatConversationKind.SUPPORT))
                .thenReturn(Optional.empty());
        when(globalPermissionService.hasPermission(
                        staff,
                        br.com.conectabyte.knowly.tenancy.GlobalPermission.STAFF_SUPPORT_HANDLE))
                .thenReturn(false);

        var result = service.findOwnOrClaimableChannel(staff, 10L);

        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
    }

    @Test
    void findOwnOrClaimableChannelReturnsAStaffCallersOwnClaimedTicketChannel() {
        User staff = user(2L, "staff@example.com");
        staff.setGlobalRole(br.com.conectabyte.knowly.tenancy.GlobalRole.STAFF);
        var tenant = new br.com.conectabyte.knowly.tenancy.Tenant("Tenant");
        tenant.setId(10L);
        when(chatConversationRepository.findByTenantIdAndOwnerIdAndKind(
                        10L, 2L, ChatConversationKind.SUPPORT))
                .thenReturn(Optional.empty());
        when(globalPermissionService.hasPermission(
                        staff,
                        br.com.conectabyte.knowly.tenancy.GlobalPermission.STAFF_SUPPORT_HANDLE))
                .thenReturn(true);

        ChatConversation claimedChannel = channelForTenant(200L, tenant);
        SupportTicket claimedTicket = new SupportTicket(claimedChannel);
        claimedTicket.setStatus(SupportTicketStatus.ASSIGNED);
        claimedTicket.setAssignedStaff(staff);
        when(supportTicketRepository.findByStatus(SupportTicketStatus.ASSIGNED))
                .thenReturn(java.util.List.of(claimedTicket));

        var result = service.findOwnOrClaimableChannel(staff, 10L);

        org.assertj.core.api.Assertions.assertThat(result).contains(claimedChannel);
    }
}
