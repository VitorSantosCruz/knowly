package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.chat.exception.ChatBlankSearchQueryException;
import br.com.conectabyte.knowly.chat.exception.ChatInvalidSearchDateRangeException;
import br.com.conectabyte.knowly.tenancy.TenantContext;
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

@ExtendWith(MockitoExtension.class)
class ChatMessageSearchServiceTest {

    @Mock private ChatMessageSearchRepository chatMessageSearchRepository;
    @Mock private ChatMessageSearchLocaleResolver chatMessageSearchLocaleResolver;
    @Mock private TenantContext tenantContext;

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
                        tenantContext);
        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ChatMessageSearchService.class))
                .addAppender(logAppender);
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

    @Test
    void resolvedLocaleAndFiltersAreDispatchedToTheCorrectRepositoryMethod() {
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(42L));
        when(chatMessageSearchLocaleResolver.resolve("pt-BR")).thenReturn(ChatSearchLocale.PT);
        when(chatMessageSearchRepository.searchPt(
                        eq(1L),
                        eq(42L),
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
                .searchPt(
                        1L,
                        42L,
                        "hello",
                        null,
                        null,
                        null,
                        null,
                        null,
                        ChatCursor.DEFAULT_PAGE_SIZE);
    }

    @Test
    void enResolvedLocaleDispatchesToSearchEnWithSuppliedFilters() {
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(42L));
        when(chatMessageSearchLocaleResolver.resolve(null)).thenReturn(ChatSearchLocale.EN);
        when(chatMessageSearchRepository.searchEn(
                        eq(1L),
                        eq(42L),
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
                .searchEn(1L, 42L, "hello", 9L, 7L, dateFrom, dateTo, null, 10);
    }

    // AppSec-required fail-closed
    @Test
    void noActiveTenantReturnsEmptyPageWithZeroRepositoryInteraction() {
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        var page = service.search(actor(), "hello", null, null, null, null, null, null, null);

        assertThat(page.results()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verifyNoInteractions(chatMessageSearchRepository);
    }

    @Test
    void noActiveTenantReturnsEmptyPageEvenWhenCallerIsStaffAdmin() {
        // Deliberately never stubs isStaff()/isStaffAdmin() -- REQ-5 forbids any oversight bypass
        // for this endpoint, so the service must not even consult them before failing closed.
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        var page = service.search(actor(), "hello", null, null, null, null, null, null, null);

        assertThat(page.results()).isEmpty();
        verifyNoInteractions(chatMessageSearchRepository);
    }

    @Test
    void logsActorHasQueryFilterPresenceAndResultCountButNeverTheRawQuery() {
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(42L));
        when(chatMessageSearchLocaleResolver.resolve(null)).thenReturn(ChatSearchLocale.EN);
        when(chatMessageSearchRepository.searchEn(
                        eq(1L),
                        eq(42L),
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
