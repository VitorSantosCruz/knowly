package br.com.conectabyte.knowly.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

/** Unified entity search (2026-08-10 amendment), REQ-22: {@link ConversationService#searchOwn}. */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantContext tenantContext;

    private ConversationService service;

    @BeforeEach
    void setUp() {
        service =
                new ConversationService(
                        conversationRepository, messageRepository, tenantRepository, tenantContext);
    }

    private User user(long id) {
        User user = new User("user" + id + "@example.com");
        user.setId(id);
        return user;
    }

    @Test
    void searchOwnWithNoActiveTenantReturnsEmptyWithNoRepositoryInteraction() {
        User owner = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        var result = service.searchOwn(owner, "articles", 5);

        assertThat(result).isEmpty();
        verify(conversationRepository, never())
                .searchByOwnerAndTitle(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void searchOwnBindsTheResolvedActiveTenantAndOwnerToTheRepositoryCall() {
        User owner = user(1L);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));
        Tenant tenant = new Tenant("Tenant");
        tenant.setId(10L);
        Conversation match = new Conversation(tenant, owner, "Base de artigos");
        match.setId(100L);
        when(conversationRepository.searchByOwnerAndTitle(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(10L),
                        anyString(),
                        any()))
                .thenReturn(new PageImpl<>(List.of(match)));

        var result = service.searchOwn(owner, "base", 5);

        assertThat(result).extracting("id").containsExactly(100L);
        verify(conversationRepository)
                .searchByOwnerAndTitle(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(10L),
                        anyString(),
                        any());
    }

    // --- AppSec-required regression (Gap 2, RAG half) ---

    @Test
    void searchOwnForAStaffCallerWithNoActiveTenantReturnsZeroResultsAcrossTenants() {
        User staff = user(1L);
        staff.setGlobalRole(br.com.conectabyte.knowly.tenancy.GlobalRole.STAFF);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        var result = service.searchOwn(staff, "base", 5);

        assertThat(result).isEmpty();
        verify(conversationRepository, never())
                .searchByOwnerAndTitle(anyLong(), anyLong(), anyString(), any());
    }
}
