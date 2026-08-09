package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.audit.AuditEventWriter;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.exception.ChatAccessDeniedException;
import br.com.conectabyte.knowly.chat.exception.ChatAdminAlreadyGrantedException;
import br.com.conectabyte.knowly.chat.exception.ChatConversationNotFoundException;
import br.com.conectabyte.knowly.chat.exception.ChatDuplicateParticipantException;
import br.com.conectabyte.knowly.chat.exception.ChatGroupStateConflictException;
import br.com.conectabyte.knowly.chat.exception.ChatIneligibleParticipantException;
import br.com.conectabyte.knowly.chat.exception.ChatJoinRequestConflictException;
import br.com.conectabyte.knowly.chat.exception.ChatVisibilityUnchangedException;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** chat-group-membership-management: REQ-1 through REQ-54, service-level unit tests. */
@ExtendWith(MockitoExtension.class)
class ChatGroupMembershipServiceTest {

    @Mock private ChatConversationRepository chatConversationRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatJoinRequestRepository chatJoinRequestRepository;
    @Mock private SupportTicketRepository supportTicketRepository;
    @Mock private ChatEligibilityService chatEligibilityService;
    @Mock private ChatOversightConversationLoader oversightConversationLoader;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantMembershipRepository tenantMembershipRepository;
    @Mock private PermissionService permissionService;
    @Mock private GlobalPermissionService globalPermissionService;
    @Mock private TenantContext tenantContext;
    @Mock private AuditEventWriter auditEventWriter;

    private ChatConversationService service;

    @BeforeEach
    void setUp() {
        service =
                new ChatConversationService(
                        chatConversationRepository,
                        chatParticipantRepository,
                        chatMessageRepository,
                        chatJoinRequestRepository,
                        supportTicketRepository,
                        chatEligibilityService,
                        oversightConversationLoader,
                        userRepository,
                        userProfileRepository,
                        tenantRepository,
                        tenantMembershipRepository,
                        permissionService,
                        globalPermissionService,
                        tenantContext,
                        auditEventWriter);
    }

    private User user(long id) {
        User u = new User("user" + id + "@example.com");
        u.setId(id);
        return u;
    }

    private Tenant tenant(long id) {
        Tenant t = new Tenant("Tenant " + id);
        t.setId(id);
        return t;
    }

    private ChatConversation group(long id, Tenant tenant, ChatGroupVisibility visibility) {
        ChatConversation c =
                new ChatConversation(ChatConversationKind.PEER_GROUP, tenant, "g", null);
        c.setId(id);
        c.setVisibility(visibility);
        return c;
    }

    private ChatParticipant participant(ChatConversation conversation, User user, boolean admin) {
        ChatParticipant p = new ChatParticipant(conversation, user);
        p.setAdmin(admin);
        p.setJoinedAt(Instant.now());
        return p;
    }

    private void mockLoad(ChatConversation conversation) {
        when(chatConversationRepository.findByIdRespectingFilter(conversation.getId()))
                .thenReturn(Optional.of(conversation));
    }

    // --- requireGroupAdmin / promoteToAdmin (REQ-1..REQ-6) --------------------------------

    @Test
    void promoteToAdminByCurrentAdminSucceeds() {
        User admin = user(1L);
        User target = user(2L);
        ChatConversation g = group(100L, tenant(1L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        ChatParticipant targetRow = participant(g, target, false);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(targetRow));
        when(chatParticipantRepository.findByConversationId(100L)).thenReturn(List.of());
        when(chatParticipantRepository.findByConversationIdAndAdminTrue(100L))
                .thenReturn(List.of());

        service.promoteToAdmin(admin, 100L, 2L);

        assertThat(targetRow.isAdmin()).isTrue();
    }

    @Test
    void promoteToAdminByNonAdminIsRejected() {
        User nonAdmin = user(1L);
        ChatConversation g = group(100L, tenant(1L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, nonAdmin, false)));

        assertThatThrownBy(() -> service.promoteToAdmin(nonAdmin, 100L, 2L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void promoteNonParticipantIsRejected() {
        User admin = user(1L);
        ChatConversation g = group(100L, tenant(1L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.promoteToAdmin(admin, 100L, 2L))
                .isInstanceOf(ChatConversationNotFoundException.class);
    }

    @Test
    void promoteAlreadyAdminIsRejectedAsNoOp() {
        User admin = user(1L);
        User target = user(2L);
        ChatConversation g = group(100L, tenant(1L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(participant(g, target, true)));

        assertThatThrownBy(() -> service.promoteToAdmin(admin, 100L, 2L))
                .isInstanceOf(ChatAdminAlreadyGrantedException.class);
    }

    // --- 403-matrix (AppSec follow-up note a) for a representative set of admin-only actions --

    @Test
    void addParticipantsRejectsAdminOfADifferentGroup() {
        User adminOfOtherGroup = user(1L);
        ChatConversation g = group(100L, tenant(1L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        // Admin of a different conversation -> no row at all for *this* conversation.
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addParticipants(adminOfOtherGroup, 100L, List.of(2L)))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void addParticipantsRejectsNonAdminParticipantOfThisGroup() {
        User nonAdminParticipant = user(1L);
        ChatConversation g = group(100L, tenant(1L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, nonAdminParticipant, false)));

        assertThatThrownBy(() -> service.addParticipants(nonAdminParticipant, 100L, List.of(2L)))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    // --- addParticipants (REQ-8..REQ-12) ----------------------------------------------------

    @Test
    void addParticipantsAddsEligibleNonDuplicateIdsAsNonAdmins() {
        User admin = user(1L);
        User eligible = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(false);
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(eligible));
        when(chatEligibilityService.isEligible(eligible, 10L)).thenReturn(true);
        when(chatParticipantRepository.findByConversationId(100L)).thenReturn(List.of());
        when(chatParticipantRepository.findByConversationIdAndAdminTrue(100L))
                .thenReturn(List.of());

        var result = service.addParticipants(admin, 100L, List.of(2L));

        assertThat(result.rejected()).isEmpty();
        verify(chatParticipantRepository, times(1)).save(any());
    }

    @Test
    void addParticipantsPartialSuccessReportsRejectionsWithoutDuplicating() {
        User admin = user(1L);
        User eligible = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        // 2L: already a participant.
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(true);
        // 3L: ineligible.
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 3L)).thenReturn(false);
        User ineligible = user(3L);
        when(userRepository.findByIdAndDeletedAtIsNull(3L)).thenReturn(Optional.of(ineligible));
        when(chatEligibilityService.isEligible(ineligible, 10L)).thenReturn(false);
        // 4L: valid.
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 4L)).thenReturn(false);
        when(userRepository.findByIdAndDeletedAtIsNull(4L)).thenReturn(Optional.of(eligible));
        when(chatEligibilityService.isEligible(eligible, 10L)).thenReturn(true);
        when(chatParticipantRepository.findByConversationId(100L)).thenReturn(List.of());
        when(chatParticipantRepository.findByConversationIdAndAdminTrue(100L))
                .thenReturn(List.of());

        var result = service.addParticipants(admin, 100L, List.of(2L, 3L, 4L));

        assertThat(result.rejected()).hasSize(2);
        verify(chatParticipantRepository, times(1)).save(any());
    }

    @Test
    void addParticipantsWhereEveryIdIsRejectedThrows() {
        User admin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.addParticipants(admin, 100L, List.of(2L)))
                .isInstanceOf(ChatIneligibleParticipantException.class);
    }

    @Test
    void addParticipantsAgainstNonPeerGroupIsRejected() {
        User admin = user(1L);
        ChatConversation direct =
                new ChatConversation(ChatConversationKind.PEER_DIRECT, null, null, null);
        direct.setId(100L);
        mockLoad(direct);

        assertThatThrownBy(() -> service.addParticipants(admin, 100L, List.of(2L)))
                .isInstanceOf(ChatGroupStateConflictException.class);
    }

    @Test
    void addParticipantsAgainstArchivedGroupIsRejected() {
        User admin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        g.setArchivedAt(Instant.now());
        mockLoad(g);

        assertThatThrownBy(() -> service.addParticipants(admin, 100L, List.of(2L)))
                .isInstanceOf(ChatGroupStateConflictException.class);
    }

    // --- removeParticipant (REQ-13..REQ-17) -------------------------------------------------

    @Test
    void removeParticipantByAdminRevokesAccessAndTriggersSuccessionIfNeeded() {
        User admin = user(1L);
        User target = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        ChatParticipant targetRow = participant(g, target, false);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(targetRow));
        when(chatParticipantRepository.countByConversationId(100L)).thenReturn(2L, 1L);
        when(chatParticipantRepository.countByConversationIdAndAdminTrue(100L)).thenReturn(1L);
        when(chatParticipantRepository.findByConversationId(100L)).thenReturn(List.of());
        when(chatParticipantRepository.findByConversationIdAndAdminTrue(100L))
                .thenReturn(List.of());

        service.removeParticipant(admin, 100L, 2L);

        verify(chatParticipantRepository).delete(targetRow);
    }

    @Test
    void removeParticipantByNonAdminIsRejected() {
        User nonAdmin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, nonAdmin, false)));

        assertThatThrownBy(() -> service.removeParticipant(nonAdmin, 100L, 2L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void removeNonParticipantIsRejected() {
        User admin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeParticipant(admin, 100L, 2L))
                .isInstanceOf(ChatConversationNotFoundException.class);
    }

    @Test
    void removingLastRemainingParticipantIsRejected() {
        User admin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        when(chatParticipantRepository.countByConversationId(100L)).thenReturn(1L);

        assertThatThrownBy(() -> service.removeParticipant(admin, 100L, 1L))
                .isInstanceOf(ChatGroupStateConflictException.class);
        verify(chatParticipantRepository, never()).delete(any());
    }

    @Test
    void removeParticipantAgainstDifferentGroupAdminIsRejected() {
        User adminOfOtherGroup = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeParticipant(adminOfOtherGroup, 100L, 2L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    // --- leaveConversation + archival (REQ-18..REQ-21, REQ-43..47) --------------------------

    @Test
    void anyParticipantCanLeaveRegardlessOfAdminStatus() {
        User participantUser = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        ChatParticipant own = participant(g, participantUser, false);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(own));
        when(chatParticipantRepository.countByConversationId(100L)).thenReturn(1L);
        when(chatParticipantRepository.countByConversationIdAndAdminTrue(100L)).thenReturn(1L);

        service.leaveConversation(participantUser, 100L);

        verify(chatParticipantRepository).delete(own);
    }

    @Test
    void nonParticipantCannotLeave() {
        User notAParticipant = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaveConversation(notAParticipant, 100L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void lastParticipantLeavingAPrivateGroupArchivesIt() {
        User last = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, last, true)));
        when(chatParticipantRepository.countByConversationId(100L)).thenReturn(0L);

        service.leaveConversation(last, 100L);

        assertThat(g.getArchivedAt()).isNotNull();
        verify(chatConversationRepository).save(g);
    }

    @Test
    void lastParticipantLeavingAPublicGroupDoesNotArchiveIt() {
        User last = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PUBLIC);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, last, true)));
        when(chatParticipantRepository.countByConversationId(100L)).thenReturn(0L);

        service.leaveConversation(last, 100L);

        assertThat(g.getArchivedAt()).isNull();
        verify(chatConversationRepository, never()).save(any());
    }

    // --- REQ-54: automatic admin succession -------------------------------------------------

    @Test
    void soleAdminLeavingWithOthersRemainingPromotesTheLongestTenured() {
        User soleAdmin = user(1L);
        User senior = user(2L);
        User junior = user(3L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, soleAdmin, true)));
        when(chatParticipantRepository.countByConversationId(100L)).thenReturn(2L);
        when(chatParticipantRepository.countByConversationIdAndAdminTrue(100L)).thenReturn(0L);
        ChatParticipant seniorRow = participant(g, senior, false);
        ChatParticipant juniorRow = participant(g, junior, false);
        when(chatParticipantRepository.findRemainingOrderedBySeniority(100L))
                .thenReturn(List.of(seniorRow, juniorRow));

        service.leaveConversation(soleAdmin, 100L);

        assertThat(seniorRow.isAdmin()).isTrue();
        assertThat(juniorRow.isAdmin()).isFalse();
    }

    @Test
    void soleAdminRemovedByAnotherAdminAlsoTriggersSuccession() {
        User anotherAdmin = user(1L);
        User soleOtherAdmin = user(2L);
        User survivor = user(3L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, anotherAdmin, true)));
        ChatParticipant targetRow = participant(g, soleOtherAdmin, true);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(targetRow));
        when(chatParticipantRepository.countByConversationId(100L)).thenReturn(3L, 2L);
        // Repository-reported post-removal admin count reaching zero (REQ-54's precondition) --
        // exercising handleAdminDepartureIfNeeded's succession branch through the removeParticipant
        // entry point, same as leaveConversation's own succession test above.
        when(chatParticipantRepository.countByConversationIdAndAdminTrue(100L)).thenReturn(0L);
        ChatParticipant survivorRow = participant(g, survivor, false);
        when(chatParticipantRepository.findRemainingOrderedBySeniority(100L))
                .thenReturn(List.of(survivorRow));

        service.removeParticipant(anotherAdmin, 100L, 2L);

        assertThat(survivorRow.isAdmin()).isTrue();
    }

    @Test
    void multiAdminLeaveDoesNotTriggerSuccession() {
        User leavingAdmin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, leavingAdmin, true)));
        when(chatParticipantRepository.countByConversationId(100L)).thenReturn(1L);
        when(chatParticipantRepository.countByConversationIdAndAdminTrue(100L)).thenReturn(1L);

        service.leaveConversation(leavingAdmin, 100L);

        verify(chatParticipantRepository, never()).findRemainingOrderedBySeniority(anyLong());
    }

    // --- Visibility (REQ-22..REQ-26) --------------------------------------------------------

    @Test
    void adminCanChangeVisibility() {
        User admin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        when(chatParticipantRepository.findByConversationId(100L)).thenReturn(List.of());
        when(chatParticipantRepository.findByConversationIdAndAdminTrue(100L))
                .thenReturn(List.of());

        service.changeVisibility(admin, 100L, ChatGroupVisibility.PUBLIC);

        assertThat(g.getVisibility()).isEqualTo(ChatGroupVisibility.PUBLIC);
    }

    @Test
    void nonAdminCannotChangeVisibility() {
        User nonAdmin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, nonAdmin, false)));

        assertThatThrownBy(
                        () -> service.changeVisibility(nonAdmin, 100L, ChatGroupVisibility.PUBLIC))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void changingToTheSameVisibilityIsRejected() {
        User admin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));

        assertThatThrownBy(() -> service.changeVisibility(admin, 100L, ChatGroupVisibility.PRIVATE))
                .isInstanceOf(ChatVisibilityUnchangedException.class);
    }

    @Test
    void changingVisibilityOnAnArchivedGroupIsRejected() {
        User admin = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        g.setArchivedAt(Instant.now());
        mockLoad(g);

        assertThatThrownBy(() -> service.changeVisibility(admin, 100L, ChatGroupVisibility.PUBLIC))
                .isInstanceOf(ChatGroupStateConflictException.class);
    }

    // --- Discovery (REQ-27/28) ---------------------------------------------------------------

    @Test
    void discoveryExcludesIneligibleAndAlreadyJoinedGroups() {
        User actor = user(1L);
        ChatConversation eligibleNotJoined = group(100L, tenant(10L), ChatGroupVisibility.PUBLIC);
        ChatConversation ineligible = group(101L, tenant(11L), ChatGroupVisibility.PUBLIC);
        ChatConversation alreadyJoined = group(102L, tenant(12L), ChatGroupVisibility.PUBLIC);

        var page =
                new org.springframework.data.domain.PageImpl<>(
                        List.of(eligibleNotJoined, ineligible, alreadyJoined));
        when(chatConversationRepository.findDiscoverable(any())).thenReturn(page);
        when(chatEligibilityService.isEligible(actor, 10L)).thenReturn(true);
        when(chatEligibilityService.isEligible(actor, 11L)).thenReturn(false);
        when(chatEligibilityService.isEligible(actor, 12L)).thenReturn(true);
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(false);
        when(chatParticipantRepository.existsByConversationIdAndUserId(102L, 1L)).thenReturn(true);
        when(chatParticipantRepository.countByConversationId(100L)).thenReturn(1L);

        var result =
                service.listDiscoverableGroups(
                        actor, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(100L);
    }

    // --- Join requests (REQ-29..REQ-37, REQ-30a) --------------------------------------------

    @Test
    void eligibleNonParticipantCanSubmitJoinRequest() {
        User requester = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.REQUEST_TO_JOIN);
        mockLoad(g);
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(false);
        when(chatEligibilityService.isEligible(requester, 10L)).thenReturn(true);
        when(chatJoinRequestRepository.findByConversationIdAndRequesterIdAndStatus(
                        100L, 2L, ChatJoinRequestStatus.PENDING))
                .thenReturn(Optional.empty());
        when(chatJoinRequestRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            ChatJoinRequest r = invocation.getArgument(0);
                            r.setId(1L);
                            r.setCreatedAt(Instant.now());
                            return r;
                        });

        var dto = service.submitJoinRequest(requester, 100L);

        assertThat(dto.status()).isEqualTo(ChatJoinRequestStatus.PENDING);
    }

    @Test
    void alreadyParticipantSubmittingJoinRequestIsRejected() {
        User requester = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.REQUEST_TO_JOIN);
        mockLoad(g);
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.submitJoinRequest(requester, 100L))
                .isInstanceOf(ChatDuplicateParticipantException.class);
    }

    @Test
    void duplicatePendingJoinRequestIsRejected() {
        User requester = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.REQUEST_TO_JOIN);
        mockLoad(g);
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(false);
        when(chatEligibilityService.isEligible(requester, 10L)).thenReturn(true);
        when(chatJoinRequestRepository.findByConversationIdAndRequesterIdAndStatus(
                        100L, 2L, ChatJoinRequestStatus.PENDING))
                .thenReturn(Optional.of(new ChatJoinRequest(g, requester)));

        assertThatThrownBy(() -> service.submitJoinRequest(requester, 100L))
                .isInstanceOf(ChatJoinRequestConflictException.class);
    }

    @Test
    void ineligibleUserSubmittingJoinRequestIsRejected() {
        User requester = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.REQUEST_TO_JOIN);
        mockLoad(g);
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(false);
        when(chatEligibilityService.isEligible(requester, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.submitJoinRequest(requester, 100L))
                .isInstanceOf(ChatIneligibleParticipantException.class);
    }

    @Test
    void joinRequestAgainstNonRequestToJoinGroupIsRejected() {
        User requester = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);

        assertThatThrownBy(() -> service.submitJoinRequest(requester, 100L))
                .isInstanceOf(ChatGroupStateConflictException.class);
    }

    @Test
    void adminApprovingAStillEligibleRequestAddsParticipant() {
        User admin = user(1L);
        User requester = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.REQUEST_TO_JOIN);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        ChatJoinRequest request = new ChatJoinRequest(g, requester);
        request.setId(5L);
        when(chatJoinRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(chatEligibilityService.isEligible(requester, 10L)).thenReturn(true);

        var dto = service.approveJoinRequest(admin, 100L, 5L);

        assertThat(dto.status()).isEqualTo(ChatJoinRequestStatus.APPROVED);
        verify(chatParticipantRepository).save(any());
    }

    @Test
    void reqThirtyA_approvalTimeEligibilityRevocationRejectsAndLeavesRequestPending() {
        User admin = user(1L);
        User requester = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.REQUEST_TO_JOIN);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        ChatJoinRequest request = new ChatJoinRequest(g, requester);
        request.setId(5L);
        when(chatJoinRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        // Eligible at submission (not re-checked here), but no longer eligible at approval time.
        when(chatEligibilityService.isEligible(requester, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.approveJoinRequest(admin, 100L, 5L))
                .isInstanceOf(ChatIneligibleParticipantException.class);

        assertThat(request.getStatus()).isEqualTo(ChatJoinRequestStatus.PENDING);
        verify(chatParticipantRepository, never()).save(any());
    }

    @Test
    void rejectingAPendingRequestMarksItRejectedWithNoParticipantCreated() {
        User admin = user(1L);
        User requester = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.REQUEST_TO_JOIN);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        ChatJoinRequest request = new ChatJoinRequest(g, requester);
        request.setId(5L);
        when(chatJoinRequestRepository.findById(5L)).thenReturn(Optional.of(request));

        var dto = service.rejectJoinRequest(admin, 100L, 5L);

        assertThat(dto.status()).isEqualTo(ChatJoinRequestStatus.REJECTED);
        verify(chatParticipantRepository, never()).save(any());
    }

    @Test
    void decidingAnAlreadyDecidedRequestIsRejected() {
        User admin = user(1L);
        User requester = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.REQUEST_TO_JOIN);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(participant(g, admin, true)));
        ChatJoinRequest request = new ChatJoinRequest(g, requester);
        request.setId(5L);
        request.setStatus(ChatJoinRequestStatus.APPROVED);
        when(chatJoinRequestRepository.findById(5L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approveJoinRequest(admin, 100L, 5L))
                .isInstanceOf(ChatJoinRequestConflictException.class);
    }

    @Test
    void approveJoinRequestRejectsAdminOfADifferentGroup() {
        User adminOfOtherGroup = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.REQUEST_TO_JOIN);
        mockLoad(g);
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveJoinRequest(adminOfOtherGroup, 100L, 5L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    // --- Direct join to a PUBLIC group (REQ-38..REQ-42) -------------------------------------

    @Test
    void eligibleNonParticipantJoinsPublicGroupImmediately() {
        User actor = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PUBLIC);
        mockLoad(g);
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(false);
        when(chatEligibilityService.isEligible(actor, 10L)).thenReturn(true);
        when(chatParticipantRepository.findByConversationId(100L)).thenReturn(List.of());
        when(chatParticipantRepository.findByConversationIdAndAdminTrue(100L))
                .thenReturn(List.of());

        service.joinPublicGroup(actor, 100L);

        verify(chatParticipantRepository).save(any());
    }

    @Test
    void directJoinAsExistingParticipantIsRejected() {
        User actor = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PUBLIC);
        mockLoad(g);
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.joinPublicGroup(actor, 100L))
                .isInstanceOf(ChatDuplicateParticipantException.class);
    }

    @Test
    void directJoinByIneligibleUserIsRejected() {
        User actor = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PUBLIC);
        mockLoad(g);
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(false);
        when(chatEligibilityService.isEligible(actor, 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.joinPublicGroup(actor, 100L))
                .isInstanceOf(ChatIneligibleParticipantException.class);
    }

    @Test
    void directJoinAgainstNonPublicGroupIsRejected() {
        User actor = user(2L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        mockLoad(g);

        assertThatThrownBy(() -> service.joinPublicGroup(actor, 100L))
                .isInstanceOf(ChatGroupStateConflictException.class);
    }

    // --- Archived-group staff visibility (REQ-44/45/46) -------------------------------------

    @Test
    void archivedTenantGroupHistoryIsReadableByAnyStaffRole() {
        User staff = user(1L);
        staff.setGlobalRole(GlobalRole.STAFF);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        g.setArchivedAt(Instant.now());
        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.empty());
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(false);
        when(oversightConversationLoader.loadIgnoringTenantFilter(100L)).thenReturn(Optional.of(g));

        ChatConversation result = service.requireReadableConversation(staff, 100L);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void archivedStaffGroupHistoryIsReadableOnlyByStaffAdmin() {
        User plainStaff = user(1L);
        plainStaff.setGlobalRole(GlobalRole.STAFF);
        ChatConversation g = group(100L, null, ChatGroupVisibility.PRIVATE);
        g.setArchivedAt(Instant.now());
        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.empty());
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(false);
        when(oversightConversationLoader.loadIgnoringTenantFilter(100L)).thenReturn(Optional.of(g));
        when(tenantContext.isStaffAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.requireReadableConversation(plainStaff, 100L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void archivedStaffGroupHistoryIsReadableByStaffAdmin() {
        User staffAdmin = user(1L);
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        ChatConversation g = group(100L, null, ChatGroupVisibility.PRIVATE);
        g.setArchivedAt(Instant.now());
        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.empty());
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(false);
        when(oversightConversationLoader.loadIgnoringTenantFilter(100L)).thenReturn(Optional.of(g));
        when(tenantContext.isStaffAdmin()).thenReturn(true);

        ChatConversation result = service.requireReadableConversation(staffAdmin, 100L);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void formerParticipantWithNoOtherRoleCannotReadArchivedGroup() {
        User formerParticipant = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        g.setArchivedAt(Instant.now());
        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.empty());
        when(chatParticipantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(false);
        when(oversightConversationLoader.loadIgnoringTenantFilter(100L)).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.requireReadableConversation(formerParticipant, 100L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    // --- Deletion (REQ-48..REQ-53) -----------------------------------------------------------

    @Test
    void staffAdminCanDeleteAnyGroupUnconditionally() {
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        when(chatConversationRepository.findByIdRespectingFilter(100L)).thenReturn(Optional.of(g));
        when(tenantContext.isStaffAdmin()).thenReturn(true);

        service.deleteConversation(user(999L), 100L);

        assertThat(g.getDeletedAt()).isNotNull();
        verify(chatParticipantRepository).softDeleteAllByConversationId(anyLong(), any());
        verify(chatMessageRepository).softDeleteAllByConversationId(anyLong(), any());
    }

    @Test
    void memberAdminCanDeleteATenantGroupTheyAdministerButNotAStaffGroupOrOtherTenant() {
        User memberAdmin = user(1L);
        Tenant administeredTenant = tenant(10L);
        Tenant otherTenant = tenant(11L);
        ChatConversation ownTenantGroup =
                group(100L, administeredTenant, ChatGroupVisibility.PRIVATE);
        ChatConversation otherTenantGroup = group(101L, otherTenant, ChatGroupVisibility.PRIVATE);
        ChatConversation staffGroup = group(102L, null, ChatGroupVisibility.PRIVATE);

        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.of(ownTenantGroup));
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        TenantMembership adminMembership =
                new TenantMembership(memberAdmin, administeredTenant, MembershipRole.MEMBER_ADMIN);
        adminMembership.setActive(true);
        when(tenantMembershipRepository.findByUserAndTenant(memberAdmin, administeredTenant))
                .thenReturn(Optional.of(adminMembership));

        service.deleteConversation(memberAdmin, 100L);
        assertThat(ownTenantGroup.getDeletedAt()).isNotNull();

        when(chatConversationRepository.findByIdRespectingFilter(101L))
                .thenReturn(Optional.of(otherTenantGroup));
        when(tenantMembershipRepository.findByUserAndTenant(memberAdmin, otherTenant))
                .thenReturn(Optional.empty());
        when(chatParticipantRepository.findByConversationIdAndUserId(101L, 1L))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteConversation(memberAdmin, 101L))
                .isInstanceOf(ChatAccessDeniedException.class);

        when(chatConversationRepository.findByIdRespectingFilter(102L))
                .thenReturn(Optional.of(staffGroup));
        when(chatParticipantRepository.findByConversationIdAndUserId(102L, 1L))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteConversation(memberAdmin, 102L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void chatGroupDeleteHolderCanDeleteThatTenantsGroupButNotAStaffGroupOrOtherTenant() {
        User holder = user(1L);
        Tenant grantedTenant = tenant(10L);
        ChatConversation ownTenantGroup = group(100L, grantedTenant, ChatGroupVisibility.PRIVATE);
        ChatConversation staffGroup = group(102L, null, ChatGroupVisibility.PRIVATE);

        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.of(ownTenantGroup));
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        TenantMembership membership =
                new TenantMembership(holder, grantedTenant, MembershipRole.MEMBER);
        membership.setActive(true);
        when(tenantMembershipRepository.findByUserAndTenant(holder, grantedTenant))
                .thenReturn(Optional.of(membership));
        when(permissionService.hasPermission(membership, Permission.CHAT_GROUP_DELETE))
                .thenReturn(true);

        service.deleteConversation(holder, 100L);
        assertThat(ownTenantGroup.getDeletedAt()).isNotNull();

        when(chatConversationRepository.findByIdRespectingFilter(102L))
                .thenReturn(Optional.of(staffGroup));
        when(chatParticipantRepository.findByConversationIdAndUserId(102L, 1L))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteConversation(holder, 102L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void groupAdminCanDeleteTheirOwnGroupWithNoTenantOrPlatformRole() {
        User groupAdmin = user(1L);
        ChatConversation staffGroup = group(102L, null, ChatGroupVisibility.PRIVATE);
        when(chatConversationRepository.findByIdRespectingFilter(102L))
                .thenReturn(Optional.of(staffGroup));
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(chatParticipantRepository.findByConversationIdAndUserId(102L, 1L))
                .thenReturn(Optional.of(participant(staffGroup, groupAdmin, true)));

        service.deleteConversation(groupAdmin, 102L);

        assertThat(staffGroup.getDeletedAt()).isNotNull();
    }

    @Test
    void callerQualifyingUnderNoPathIsRejected() {
        User nobody = user(1L);
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        when(chatConversationRepository.findByIdRespectingFilter(100L)).thenReturn(Optional.of(g));
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(tenantMembershipRepository.findByUserAndTenant(nobody, g.getTenant()))
                .thenReturn(Optional.empty());
        when(chatParticipantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteConversation(nobody, 100L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void deletingANonExistentConversationReturnsNotFound() {
        when(chatConversationRepository.findByIdRespectingFilter(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteConversation(user(1L), 999L))
                .isInstanceOf(ChatConversationNotFoundException.class);
    }

    @Test
    void deletingANonPeerGroupConversationIsRejected() {
        ChatConversation direct =
                new ChatConversation(ChatConversationKind.PEER_DIRECT, null, null, null);
        direct.setId(100L);
        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.of(direct));

        assertThatThrownBy(() -> service.deleteConversation(user(1L), 100L))
                .isInstanceOf(ChatGroupStateConflictException.class);
    }

    @Test
    void deletingAnAlreadyDeletedConversationIsRejectedNotANoOp() {
        ChatConversation g = group(100L, tenant(10L), ChatGroupVisibility.PRIVATE);
        g.setDeletedAt(Instant.now());
        when(chatConversationRepository.findByIdRespectingFilter(100L)).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.deleteConversation(user(1L), 100L))
                .isInstanceOf(ChatGroupStateConflictException.class);
    }
}
