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
                        ChatConversationRequestKind.DIRECT, null, null, java.util.List.of(2L));

        assertThatThrownBy(() -> service.createConversation(actor, request))
                .isInstanceOf(ChatConversationNotFoundException.class);
    }

    @Test
    void createGroupConversationFailsWhenAParticipantIsSoftDeleted() {
        User actor = user(1L);
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.empty());

        var request =
                new CreateChatConversationRequestDto(
                        ChatConversationRequestKind.GROUP, 10L, "g", java.util.List.of(2L));

        assertThatThrownBy(() -> service.createConversation(actor, request))
                .isInstanceOf(ChatConversationNotFoundException.class);
    }
}
