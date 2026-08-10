package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.chat.exception.ChatBlankSearchQueryException;
import br.com.conectabyte.knowly.chat.exception.ChatInvalidSearchDateRangeException;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Covers REQ-11/12/13/14 (input validation, locale dispatch) and REQ-5e-REQ-5j (role-based scoping)
 * at the service layer, via Mockito.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageSearchServiceTest {

    @Mock private ChatMessageSearchRepository chatMessageSearchRepository;
    @Mock private ChatMessageSearchLocaleResolver chatMessageSearchLocaleResolver;
    @Mock private TenantContext tenantContext;
    @Mock private TenantMembershipRepository tenantMembershipRepository;
    @Mock private ChatConversationRepository chatConversationRepository;
    @Mock private ChatEligibilityService chatEligibilityService;

    private ChatMessageSearchService service;
    private ListAppender<ILoggingEvent> logAppender;

    private User actor() {
        User user = new User("search-actor@example.com");
        user.setId(1L);
        return user;
    }

    @BeforeEach
    void setUp() {
        service =
                new ChatMessageSearchService(
                        chatMessageSearchRepository,
                        chatMessageSearchLocaleResolver,
                        tenantContext,
                        tenantMembershipRepository,
                        chatConversationRepository,
                        chatEligibilityService);
        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ChatMessageSearchService.class))
                .addAppender(logAppender);
        lenient()
                .when(tenantMembershipRepository.findByUserAndActiveTrue(any()))
                .thenReturn(List.of());
        lenient().when(chatConversationRepository.findDiscoverableIds(any())).thenReturn(List.of());
        lenient()
                .when(chatConversationRepository.findDiscoverableIdsPlatformWide())
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ChatMessageSearchService.class))
                .detachAppender(logAppender);
    }

    // REQ-11
    @Test
    void blankQueryThrowsBeforeAnyRepositoryInteraction() {
        assertThatThrownBy(
                        () ->
                                service.search(
                                        actor(), "   ", null, null, null, null, null, null, null))
                .isInstanceOf(ChatBlankSearchQueryException.class);

        verifyNoInteractions(chatMessageSearchRepository);
    }

    @Test
    void missingQueryThrowsBeforeAnyRepositoryInteraction() {
        assertThatThrownBy(
                        () ->
                                service.search(
                                        actor(), null, null, null, null, null, null, null, null))
                .isInstanceOf(ChatBlankSearchQueryException.class);

        verifyNoInteractions(chatMessageSearchRepository);
    }

    // REQ-12
    @Test
    void dateFromAfterDateToThrowsBeforeAnyRepositoryInteraction() {
        Instant dateFrom = Instant.now();
        Instant dateTo = dateFrom.minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(
                        () ->
                                service.search(
                                        actor(), "hello", null, null, dateFrom, dateTo, null, null,
                                        null))
                .isInstanceOf(ChatInvalidSearchDateRangeException.class);

        verifyNoInteractions(chatMessageSearchRepository);
    }

    // REQ-5h: ordinary tenant MEMBER dispatches to searchScopedPt/En, tenant-bound.
    @Test
    void tenantMemberDispatchesToScopedSearchWithResolvedLocaleAndFilters() {
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(42L));
        when(chatMessageSearchLocaleResolver.resolve("pt-BR")).thenReturn(ChatSearchLocale.PT);
        when(chatMessageSearchRepository.searchScopedPt(
                        eq(1L),
                        eq(42L),
                        any(Long[].class),
                        eq("hello"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        anyInt()))
                .thenReturn(List.of());

        service.search(actor(), "hello", null, null, null, null, null, null, "pt-BR");

        verify(chatMessageSearchRepository)
                .searchScopedPt(
                        eq(1L),
                        eq(42L),
                        any(Long[].class),
                        eq("hello"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(ChatCursor.DEFAULT_PAGE_SIZE));
    }

    @Test
    void enResolvedLocaleDispatchesToScopedSearchEnWithSuppliedFilters() {
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(42L));
        when(chatMessageSearchLocaleResolver.resolve(null)).thenReturn(ChatSearchLocale.EN);
        when(chatMessageSearchRepository.searchScopedEn(
                        eq(1L),
                        eq(42L),
                        any(Long[].class),
                        eq("hello"),
                        eq(9L),
                        eq(7L),
                        any(),
                        any(),
                        eq(null),
                        anyInt()))
                .thenReturn(List.of());

        Instant dateFrom = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant dateTo = Instant.now();
        service.search(actor(), "hello", 9L, 7L, dateFrom, dateTo, null, 10, null);

        verify(chatMessageSearchRepository)
                .searchScopedEn(
                        eq(1L),
                        eq(42L),
                        any(Long[].class),
                        eq("hello"),
                        eq(9L),
                        eq(7L),
                        eq(dateFrom),
                        eq(dateTo),
                        eq(null),
                        eq(10));
    }

    // REQ-5e: STAFF_ADMIN gets platform-wide, unrestricted search regardless of active tenant.
    @Test
    void staffAdminDispatchesToUnrestrictedSearchRegardlessOfActiveTenant() {
        when(tenantContext.isStaffAdmin()).thenReturn(true);
        when(chatMessageSearchLocaleResolver.resolve(null)).thenReturn(ChatSearchLocale.EN);
        when(chatMessageSearchRepository.searchUnrestrictedEn(
                        eq("hello"), eq(null), eq(null), eq(null), eq(null), eq(null), anyInt()))
                .thenReturn(List.of());

        service.search(actor(), "hello", null, null, null, null, null, null, null);

        verify(chatMessageSearchRepository)
                .searchUnrestrictedEn(
                        "hello", null, null, null, null, null, ChatCursor.DEFAULT_PAGE_SIZE);
        verifyNoInteractions(tenantMembershipRepository);
    }

    // REQ-5g/REQ-5j: an active-tenant MEMBER_ADMIN gets tenant-unrestricted search, never
    // cross-tenant.
    @Test
    void tenantMemberAdminDispatchesToTenantUnrestrictedSearchBoundToActiveTenant() {
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(42L));
        TenantMembership membership = new TenantMembership();
        Tenant tenant = new Tenant("Tenant 42");
        tenant.setId(42L);
        membership.setTenant(tenant);
        membership.setActive(true);
        membership.setRole(MembershipRole.MEMBER_ADMIN);
        when(tenantMembershipRepository.findByUserAndActiveTrue(any()))
                .thenReturn(List.of(membership));
        when(chatMessageSearchLocaleResolver.resolve(null)).thenReturn(ChatSearchLocale.EN);
        when(chatMessageSearchRepository.searchTenantUnrestrictedEn(
                        eq(42L),
                        eq("hello"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        anyInt()))
                .thenReturn(List.of());

        service.search(actor(), "hello", null, null, null, null, null, null, null);

        verify(chatMessageSearchRepository)
                .searchTenantUnrestrictedEn(
                        42L, "hello", null, null, null, null, null, ChatCursor.DEFAULT_PAGE_SIZE);
    }

    // REQ-5j: a stale MEMBER_ADMIN role held in a *different* (non-active) tenant must never
    // trigger the tenant-unrestricted branch for the active tenant.
    @Test
    void memberAdminInADifferentTenantDoesNotTriggerTenantUnrestrictedForActiveTenant() {
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(42L));
        TenantMembership staleMembership = new TenantMembership();
        Tenant otherTenant = new Tenant("Other Tenant");
        otherTenant.setId(99L);
        staleMembership.setTenant(otherTenant);
        staleMembership.setActive(true);
        staleMembership.setRole(MembershipRole.MEMBER_ADMIN);
        when(tenantMembershipRepository.findByUserAndActiveTrue(any()))
                .thenReturn(List.of(staleMembership));
        when(chatMessageSearchLocaleResolver.resolve(null)).thenReturn(ChatSearchLocale.EN);
        when(chatMessageSearchRepository.searchScopedEn(
                        eq(1L),
                        eq(42L),
                        any(Long[].class),
                        eq("hello"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        anyInt()))
                .thenReturn(List.of());

        service.search(actor(), "hello", null, null, null, null, null, null, null);

        verify(chatMessageSearchRepository)
                .searchScopedEn(
                        eq(1L),
                        eq(42L),
                        any(Long[].class),
                        eq("hello"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(ChatCursor.DEFAULT_PAGE_SIZE));
    }

    // REQ-5f: staff with no active tenant now gets scoped (not empty) results -- staff-chat
    // parity fix, generalized by the role-based ruleset.
    @Test
    void staffWithNoActiveTenantDispatchesToScopedSearchUnboundToAnyTenant() {
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());
        when(tenantContext.isStaff()).thenReturn(true);
        when(chatMessageSearchLocaleResolver.resolve(null)).thenReturn(ChatSearchLocale.EN);
        when(chatMessageSearchRepository.searchScopedEn(
                        eq(1L),
                        eq(null),
                        any(Long[].class),
                        eq("hello"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        anyInt()))
                .thenReturn(List.of());

        service.search(actor(), "hello", null, null, null, null, null, null, null);

        verify(chatMessageSearchRepository)
                .searchScopedEn(
                        eq(1L),
                        eq(null),
                        any(Long[].class),
                        eq("hello"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(ChatCursor.DEFAULT_PAGE_SIZE));
    }

    // Fail-closed baseline unaffected by the amendment: no active tenant, not staff.
    @Test
    void noActiveTenantAndNotStaffReturnsEmptyPageWithZeroRepositoryInteraction() {
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());
        when(tenantContext.isStaff()).thenReturn(false);

        var page = service.search(actor(), "hello", null, null, null, null, null, null, null);

        assertThat(page.results()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verifyNoInteractions(chatMessageSearchRepository);
    }

    @Test
    void logsActorHasQueryFilterPresenceAndResultCountButNeverTheRawQuery() {
        when(tenantContext.isStaffAdmin()).thenReturn(false);
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(42L));
        when(chatMessageSearchLocaleResolver.resolve(null)).thenReturn(ChatSearchLocale.EN);
        when(chatMessageSearchRepository.searchScopedEn(
                        eq(1L),
                        eq(42L),
                        any(Long[].class),
                        eq("super-secret-query"),
                        eq(null),
                        eq(null),
                        any(),
                        any(),
                        eq(null),
                        anyInt()))
                .thenReturn(List.of());

        service.search(actor(), "super-secret-query", null, null, null, null, null, null, null);

        assertThat(logAppender.list).isNotEmpty();
        String logged =
                logAppender.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .reduce("", String::concat);
        assertThat(logged).doesNotContain("super-secret-query");
        assertThat(logged).contains(String.valueOf(actor().getId()));
    }
}
