package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.audit.AuditEventWriter;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.dto.CreateChatConversationRequestDto;
import br.com.conectabyte.knowly.chat.dto.CreateChatConversationRequestDto.ChatConversationRequestKind;
import br.com.conectabyte.knowly.chat.exception.ChatAccessDeniedException;
import br.com.conectabyte.knowly.chat.exception.ChatConversationNotFoundException;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.PermissionService;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatConversationServiceTest {

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
        User user = new User("user" + id + "@example.com");
        user.setId(id);
        return user;
    }

    private Tenant tenant(long id) {
        Tenant tenant = new Tenant("Tenant " + id);
        tenant.setId(id);
        return tenant;
    }

    private ChatConversation groupConversation(long id, Tenant tenant) {
        ChatConversation conversation =
                new ChatConversation(ChatConversationKind.PEER_GROUP, tenant, "g", null);
        conversation.setId(id);
        return conversation;
    }

    @Test
    void listConversationsExposesTheMostRecentMessageTimestampWhenTheConversationHasMessages() {
        User actor = user(1L);
        Tenant tenant = tenant(20L);
        ChatConversation conversation = groupConversation(100L, tenant);
        ChatParticipant participant = new ChatParticipant(conversation, actor);
        java.time.Instant lastMessageAt = java.time.Instant.parse("2026-08-08T10:00:00Z");

        when(chatParticipantRepository.findByUserId(1L)).thenReturn(java.util.List.of(participant));
        when(chatMessageRepository.findLastMessageAtByConversationIdIn(java.util.List.of(100L)))
                .thenReturn(
                        java.util.List.of(
                                new ChatMessageRepository.ConversationLastMessageAt() {
                                    @Override
                                    public Long getConversationId() {
                                        return 100L;
                                    }

                                    @Override
                                    public java.time.Instant getLastMessageAt() {
                                        return lastMessageAt;
                                    }
                                }));
        when(chatParticipantRepository.findByConversationId(100L))
                .thenReturn(java.util.List.of(participant));

        var summaries = service.listConversations(actor);

        assertThat(summaries).extracting("lastMessageAt").containsExactly(lastMessageAt);
    }

    @Test
    void listConversationsFallsBackToConversationCreatedAtWhenThereAreNoMessagesYet() {
        User actor = user(1L);
        Tenant tenant = tenant(20L);
        ChatConversation conversation = groupConversation(100L, tenant);
        ChatParticipant participant = new ChatParticipant(conversation, actor);

        when(chatParticipantRepository.findByUserId(1L)).thenReturn(java.util.List.of(participant));
        when(chatMessageRepository.findLastMessageAtByConversationIdIn(java.util.List.of(100L)))
                .thenReturn(java.util.List.of());
        when(chatParticipantRepository.findByConversationId(100L))
                .thenReturn(java.util.List.of(participant));

        var summaries = service.listConversations(actor);

        assertThat(summaries)
                .extracting("lastMessageAt")
                .containsExactly(conversation.getCreatedAt());
    }

    @Test
    void listConversationsForStaffWithActiveTenantHidesStaffOnlyDirectConversations() {
        User actor = user(1L);
        actor.setGlobalRole(br.com.conectabyte.knowly.tenancy.GlobalRole.STAFF);
        Tenant activeTenant = tenant(20L);

        ChatConversation staffOnlyConversation =
                new ChatConversation(ChatConversationKind.PEER_DIRECT, null, null, null);
        staffOnlyConversation.setId(100L);
        ChatParticipant staffOnlyParticipant = new ChatParticipant(staffOnlyConversation, actor);

        ChatConversation tenantConversation =
                new ChatConversation(ChatConversationKind.PEER_DIRECT, activeTenant, null, null);
        tenantConversation.setId(200L);
        ChatParticipant tenantParticipant = new ChatParticipant(tenantConversation, actor);

        when(chatParticipantRepository.findByUserId(1L))
                .thenReturn(java.util.List.of(staffOnlyParticipant, tenantParticipant));
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(20L));
        when(chatMessageRepository.findLastMessageAtByConversationIdIn(java.util.List.of(200L)))
                .thenReturn(java.util.List.of());
        when(chatParticipantRepository.findByConversationId(200L))
                .thenReturn(java.util.List.of(tenantParticipant));

        var summaries = service.listConversations(actor);

        assertThat(summaries).extracting("id").containsExactly(200L);
    }

    @Test
    void listConversationsForStaffWithoutActiveTenantShowsOnlyStaffOnlyDirectConversations() {
        User actor = user(1L);
        actor.setGlobalRole(br.com.conectabyte.knowly.tenancy.GlobalRole.STAFF);
        Tenant someTenant = tenant(20L);

        ChatConversation staffOnlyConversation =
                new ChatConversation(ChatConversationKind.PEER_DIRECT, null, null, null);
        staffOnlyConversation.setId(100L);
        ChatParticipant staffOnlyParticipant = new ChatParticipant(staffOnlyConversation, actor);

        ChatConversation tenantConversation =
                new ChatConversation(ChatConversationKind.PEER_DIRECT, someTenant, null, null);
        tenantConversation.setId(200L);
        ChatParticipant tenantParticipant = new ChatParticipant(tenantConversation, actor);

        when(chatParticipantRepository.findByUserId(1L))
                .thenReturn(java.util.List.of(staffOnlyParticipant, tenantParticipant));
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());
        when(chatMessageRepository.findLastMessageAtByConversationIdIn(java.util.List.of(100L)))
                .thenReturn(java.util.List.of());
        when(chatParticipantRepository.findByConversationId(100L))
                .thenReturn(java.util.List.of(staffOnlyParticipant));

        var summaries = service.listConversations(actor);

        assertThat(summaries).extracting("id").containsExactly(100L);
    }

    @Test
    void memberAdminIsRejectedFromAGroupOfATenantTheyDoNotAdminister() {
        User memberAdmin = user(1L);
        Tenant otherTenant = tenant(20L);
        ChatConversation group = groupConversation(100L, otherTenant);

        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.empty());
        when(chatParticipantRepository.existsByConversationIdAndUserId(anyLong(), anyLong()))
                .thenReturn(false);
        when(oversightConversationLoader.loadIgnoringTenantFilter(100L))
                .thenReturn(Optional.of(group));
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(tenantMembershipRepository.findByUserAndTenant(memberAdmin, otherTenant))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireReadableConversation(memberAdmin, 100L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void memberAdminIsRejectedFromAStaffOnlyGroupUnderAnyCircumstance() {
        User memberAdmin = user(1L);
        ChatConversation staffOnlyGroup = groupConversation(100L, null);

        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.empty());
        when(chatParticipantRepository.existsByConversationIdAndUserId(anyLong(), anyLong()))
                .thenReturn(false);
        when(oversightConversationLoader.loadIgnoringTenantFilter(100L))
                .thenReturn(Optional.of(staffOnlyGroup));
        when(tenantContext.isStaffAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.requireReadableConversation(memberAdmin, 100L))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void memberAdminOfTheRightTenantCanReadAGroupTheyAreNotAParticipantOf() {
        User memberAdmin = user(1L);
        Tenant administeredTenant = tenant(10L);
        ChatConversation group = groupConversation(100L, administeredTenant);

        when(chatConversationRepository.findByIdRespectingFilter(100L))
                .thenReturn(Optional.empty());
        when(chatParticipantRepository.existsByConversationIdAndUserId(anyLong(), anyLong()))
                .thenReturn(false);
        when(oversightConversationLoader.loadIgnoringTenantFilter(100L))
                .thenReturn(Optional.of(group));
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        TenantMembership activeAdminMembership =
                new TenantMembership(memberAdmin, administeredTenant, MembershipRole.MEMBER_ADMIN);
        activeAdminMembership.setActive(true);
        when(tenantMembershipRepository.findByUserAndTenant(memberAdmin, administeredTenant))
                .thenReturn(Optional.of(activeAdminMembership));

        ChatConversation result = service.requireReadableConversation(memberAdmin, 100L);

        assertThat(result.getId()).isEqualTo(100L);
    }

    // --- logical-delete-everywhere (2026-08-04): a soft-deleted user's id must not be addable ---

    @Test
    void createDirectConversationFailsWhenTargetIsSoftDeleted() {
        User actor = user(1L);
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.empty());

        var request =
                new CreateChatConversationRequestDto(
                        ChatConversationRequestKind.DIRECT,
                        null,
                        null,
                        java.util.List.of(2L),
                        null);

        assertThatThrownBy(() -> service.createConversation(actor, request))
                .isInstanceOf(ChatConversationNotFoundException.class);
    }

    @Test
    void createGroupConversationFailsWhenAParticipantIsSoftDeleted() {
        User actor = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.empty());

        var request =
                new CreateChatConversationRequestDto(
                        ChatConversationRequestKind.GROUP, 10L, "g", java.util.List.of(2L), null);

        assertThatThrownBy(() -> service.createConversation(actor, request))
                .isInstanceOf(ChatConversationNotFoundException.class);
    }

    // --- fix: a group's tenant anchor must be server-derived from the active session, never ---
    // --- taken as-is from the client-supplied request.tenantId() ---

    @Test
    void createGroupConversationAnchorsToTheActorsActiveSessionTenantRegardlessOfRequestBody() {
        User actor = user(1L);
        Tenant activeTenant = tenant(20L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(20L));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(actor));
        when(chatEligibilityService.isEligibleAsActor(actor, 20L)).thenReturn(true);
        when(tenantRepository.findById(20L)).thenReturn(Optional.of(activeTenant));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(chatConversationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Request body omits tenantId entirely -- the anchor must still come from the actor's
        // active session tenant, never default to the staff-only null anchor.
        var request =
                new CreateChatConversationRequestDto(
                        ChatConversationRequestKind.GROUP, null, "g", java.util.List.of(), null);

        var result = service.createConversation(actor, request);

        assertThat(result.tenantId()).isEqualTo(20L);
    }

    // --- Bug fix (2026-08-09): the request's visibility field was declared nowhere on the DTO, ---
    // --- so Jackson silently dropped it and every group was persisted as PRIVATE regardless of ---
    // --- what the client (chat-unified-ui REQ-13/18) actually chose. ---

    @Test
    void createGroupConversationPersistsTheRequestedVisibilityInsteadOfAlwaysDefaultingToPrivate() {
        User actor = user(1L);
        Tenant activeTenant = tenant(20L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(20L));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(actor));
        when(chatEligibilityService.isEligibleAsActor(actor, 20L)).thenReturn(true);
        when(tenantRepository.findById(20L)).thenReturn(Optional.of(activeTenant));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        var savedConversation = new java.util.concurrent.atomic.AtomicReference<ChatConversation>();
        when(chatConversationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation -> {
                            ChatConversation conversation = invocation.getArgument(0);
                            savedConversation.set(conversation);
                            return conversation;
                        });

        var request =
                new CreateChatConversationRequestDto(
                        ChatConversationRequestKind.GROUP,
                        null,
                        "g",
                        java.util.List.of(),
                        ChatGroupVisibility.PUBLIC);

        service.createConversation(actor, request);

        assertThat(savedConversation.get().getVisibility()).isEqualTo(ChatGroupVisibility.PUBLIC);
    }

    @Test
    void createGroupConversationDefaultsToPrivateVisibilityWhenRequestOmitsIt() {
        User actor = user(1L);
        Tenant activeTenant = tenant(20L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(20L));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(actor));
        when(chatEligibilityService.isEligibleAsActor(actor, 20L)).thenReturn(true);
        when(tenantRepository.findById(20L)).thenReturn(Optional.of(activeTenant));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        var savedConversation = new java.util.concurrent.atomic.AtomicReference<ChatConversation>();
        when(chatConversationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(
                        invocation -> {
                            ChatConversation conversation = invocation.getArgument(0);
                            savedConversation.set(conversation);
                            return conversation;
                        });

        var request =
                new CreateChatConversationRequestDto(
                        ChatConversationRequestKind.GROUP, null, "g", java.util.List.of(), null);

        service.createConversation(actor, request);

        assertThat(savedConversation.get().getVisibility()).isEqualTo(ChatGroupVisibility.PRIVATE);
    }

    // --- Bug fix (2026-08-09): a STAFF_ADMIN actor with an active tenant creating a group by ---
    // --- themselves (no extra participants) must not be rejected for lacking a real ---
    // --- TenantMembership row of their own -- the actor is checked via isEligibleAsActor, ---
    // --- never plain isEligible, since it's the actor's own group being created. ---

    @Test
    void staffAdminWithActiveTenantCanCreateAGroupBySelfWithNoRealTenantMembership() {
        User actor = user(1L);
        actor.setGlobalRole(br.com.conectabyte.knowly.tenancy.GlobalRole.STAFF_ADMIN);
        Tenant activeTenant = tenant(20L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(20L));
        // Deliberately a *different* User instance than `actor`, representing the same id --
        // this is what really happens in production: `actor` comes from the security
        // context/session while `participant` is freshly loaded via the repository. Reusing
        // the same reference here would mask an equals()-by-reference bug (User does not
        // override equals()/hashCode()), which is exactly what happened before this fix: see
        // ChatConversationService#createConversation using
        // participant.getId().equals(actor.getId())
        // instead of participant.equals(actor).
        User participantSameIdAsActor = user(1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(participantSameIdAsActor));
        // No real TenantMembership: plain isEligible would reject the actor, but
        // isEligibleAsActor -- called for the actor specifically -- must accept them.
        when(chatEligibilityService.isEligibleAsActor(participantSameIdAsActor, 20L))
                .thenReturn(true);
        when(tenantRepository.findById(20L)).thenReturn(Optional.of(activeTenant));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(chatConversationRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request =
                new CreateChatConversationRequestDto(
                        ChatConversationRequestKind.GROUP, null, "g", java.util.List.of(), null);

        var result = service.createConversation(actor, request);

        assertThat(result.tenantId()).isEqualTo(20L);
    }

    @Test
    void addingAParticipantWithNoRealTenantMembershipIsStillRejectedEvenWhenActorIsEligible() {
        User actor = user(1L);
        User otherParticipant = user(2L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(20L));
        when(userRepository.findByIdAndDeletedAtIsNull(2L))
                .thenReturn(Optional.of(otherParticipant));
        // The other participant has no real membership -- must still be rejected, unweakened
        // (this fails before the actor's own isEligibleAsActor check is even reached).
        when(chatEligibilityService.isEligible(otherParticipant, 20L)).thenReturn(false);

        var request =
                new CreateChatConversationRequestDto(
                        ChatConversationRequestKind.GROUP, null, "g", java.util.List.of(2L), null);

        assertThatThrownBy(() -> service.createConversation(actor, request))
                .isInstanceOf(
                        br.com.conectabyte.knowly.chat.exception.ChatIneligibleParticipantException
                                .class);
    }

    @Test
    void createGroupConversationRejectsARequestTenantIdThatDoesNotMatchTheActiveSessionTenant() {
        User actor = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        var request =
                new CreateChatConversationRequestDto(
                        ChatConversationRequestKind.GROUP, 999L, "g", java.util.List.of(), null);

        assertThatThrownBy(() -> service.createConversation(actor, request))
                .isInstanceOf(ChatAccessDeniedException.class);
    }
}
