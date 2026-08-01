package br.com.conectabyte.knowly.metrics.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.conversation.Message;
import br.com.conectabyte.knowly.conversation.MessageArticleCitation;
import br.com.conectabyte.knowly.conversation.MessageArticleCitationRepository;
import br.com.conectabyte.knowly.conversation.MessageRepository;
import br.com.conectabyte.knowly.conversation.MessageRole;
import br.com.conectabyte.knowly.metrics.DailyCountDto;
import br.com.conectabyte.knowly.metrics.MetricsPeriod;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * specify/features/global-staff-dashboard-metrics/SPEC.md REQ-1/3/4/5/6/8/9: {@link
 * GlobalMetricsService#globalMetrics()} aggregates counts across every tenant, gated by {@link
 * GlobalPermission#DASHBOARD_VIEW_GLOBAL}.
 */
@Import({TestcontainersConfiguration.class, GlobalMetricsServiceTest.FixedClockConfig.class})
@SpringBootTest
@ActiveProfiles("test")
// Overrides the app's production `clock` bean (ClockConfig) with FixedClockConfig's fixed Clock
// for deterministic month-boundary assertions; Spring 4.1 rejects same-name bean redefinition by
// default even with @Primary, so overriding must be explicitly allowed for this context only.
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class GlobalMetricsServiceTest {

    // 2026-07-26T12:00:00Z is mid-July: start of the current UTC calendar month is 2026-07-01.
    static final Instant FIXED_NOW = Instant.parse("2026-07-26T12:00:00Z");
    static final Instant START_OF_CURRENT_UTC_MONTH = Instant.parse("2026-07-01T00:00:00Z");

    @Autowired private GlobalMetricsService globalMetricsService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MessageArticleCitationRepository messageArticleCitationRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private User staffAdmin(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF_ADMIN);
        return userRepository.saveAndFlush(user);
    }

    private User staff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        return userRepository.saveAndFlush(user);
    }

    private void backdateTenant(Tenant tenant, Instant createdAt) {
        jdbcTemplate.update(
                "update tenants set created_at = ? where id = ?",
                Timestamp.from(createdAt),
                tenant.getId());
    }

    private void backdateUser(User user, Instant createdAt) {
        jdbcTemplate.update(
                "update users set created_at = ? where id = ?",
                Timestamp.from(createdAt),
                user.getId());
    }

    // --- REQ-1/3/4/5/6: correct counts, using the injected (fixed) Clock ---

    @Test
    void returnsCorrectCountsAcrossAllTenants() {
        // Baselines must be captured before the STAFF_ADMIN actor is created below, since that
        // actor itself counts toward staffCount (the assertions below expect baseline + 2: the
        // actor plus the new STAFF user created later in this test).
        long tenantCountBaseline = tenantRepository.count();
        long newTenantsBaseline =
                tenantRepository.countByCreatedAtGreaterThanEqual(START_OF_CURRENT_UTC_MONTH);
        long articlesReadBaseline = messageArticleCitationRepository.count();
        long staffCountBaseline =
                userRepository.countByGlobalRoleIn(
                        List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN));

        staffAdmin("global-metrics-actor@example.com");
        authenticateAs("global-metrics-actor@example.com");
        tenantContext.setStaffAdmin(true);

        Tenant currentMonthTenant = tenantRepository.saveAndFlush(new Tenant("Current Month Co"));
        backdateTenant(currentMonthTenant, START_OF_CURRENT_UTC_MONTH.plus(1, ChronoUnit.DAYS));

        Tenant previousMonthTenant = tenantRepository.saveAndFlush(new Tenant("Previous Month Co"));
        backdateTenant(previousMonthTenant, START_OF_CURRENT_UTC_MONTH.minus(1, ChronoUnit.DAYS));

        Article article =
                articleRepository.saveAndFlush(
                        new Article(
                                currentMonthTenant,
                                "Global Metrics Article",
                                "key",
                                "file.pdf",
                                "application/pdf"));
        User citationOwner =
                userRepository.saveAndFlush(new User("global-metrics-citation-owner@example.com"));
        Conversation conversation =
                conversationRepository.saveAndFlush(
                        new Conversation(currentMonthTenant, citationOwner));
        Message message =
                messageRepository.saveAndFlush(
                        new Message(conversation, MessageRole.ASSISTANT, "hi"));
        messageArticleCitationRepository.saveAndFlush(new MessageArticleCitation(message, article));

        staff("global-metrics-new-staff@example.com");

        GlobalMetricsDto result = globalMetricsService.globalMetrics();

        assertThat(result.tenantCount()).isEqualTo(tenantCountBaseline + 2);
        assertThat(result.newTenantsThisMonth()).isEqualTo(newTenantsBaseline + 1);
        assertThat(result.articlesReadTotal()).isEqualTo(articlesReadBaseline + 1);
        assertThat(result.staffCount()).isEqualTo(staffCountBaseline + 2);
    }

    @Test
    void newTenantsThisMonthBoundaryIsExactAtTheUtcMonthStart() {
        long newTenantsBaseline =
                tenantRepository.countByCreatedAtGreaterThanEqual(START_OF_CURRENT_UTC_MONTH);

        staffAdmin("global-metrics-boundary-actor@example.com");
        authenticateAs("global-metrics-boundary-actor@example.com");
        tenantContext.setStaffAdmin(true);

        Tenant lastInstantOfPreviousMonth =
                tenantRepository.saveAndFlush(new Tenant("Boundary Co 1"));
        backdateTenant(lastInstantOfPreviousMonth, START_OF_CURRENT_UTC_MONTH.minusMillis(1));

        Tenant firstInstantOfCurrentMonth =
                tenantRepository.saveAndFlush(new Tenant("Boundary Co 2"));
        backdateTenant(firstInstantOfCurrentMonth, START_OF_CURRENT_UTC_MONTH);

        GlobalMetricsDto result = globalMetricsService.globalMetrics();

        assertThat(result.newTenantsThisMonth()).isEqualTo(newTenantsBaseline + 1);
    }

    // --- REQ-9: STAFF caller without the grant is rejected ---

    @Test
    void staffWithoutGrantIsRejected() {
        staff("global-metrics-no-grant@example.com");
        authenticateAs("global-metrics-no-grant@example.com");

        assertThatThrownBy(globalMetricsService::globalMetrics)
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- REQ-8: STAFF caller holding the grant succeeds ---

    @Test
    void staffWithGrantSucceeds() {
        User actor = staff("global-metrics-granted@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(actor, GlobalPermission.DASHBOARD_VIEW_GLOBAL));
        authenticateAs("global-metrics-granted@example.com");

        assertThat(globalMetricsService.globalMetrics()).isNotNull();
    }

    // --- REQ-8: STAFF_ADMIN succeeds without any explicit grant ---

    @Test
    void staffAdminSucceedsWithoutExplicitGrant() {
        staffAdmin("global-metrics-admin@example.com");
        authenticateAs("global-metrics-admin@example.com");
        tenantContext.setStaffAdmin(true);

        assertThat(globalMetricsService.globalMetrics()).isNotNull();
    }

    // --- REQ-10: tenant MEMBER/MEMBER_ADMIN with no GlobalRole is rejected regardless of
    // tenant-side permissions ---

    @Test
    void tenantMemberWithNoGlobalRoleIsRejected() {
        User user = userRepository.saveAndFlush(new User("global-metrics-member@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Member Tenant Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER_ADMIN));
        authenticateAs("global-metrics-member@example.com");
        tenantContext.setActiveTenantId(tenant.getId());

        assertThatThrownBy(globalMetricsService::globalMetrics)
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- specify/features/global-staff-dashboard-trends/SPEC.md REQ-2/3/4/5/6 ---

    private void backdateCitation(MessageArticleCitation citation, Instant createdAt) {
        jdbcTemplate.update(
                "update message_article_citations set created_at = ? where id = ?",
                Timestamp.from(createdAt),
                citation.getId());
    }

    private MessageArticleCitation citationAt(Instant createdAt) {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Trends Citation Co"));
        Article article =
                articleRepository.saveAndFlush(
                        new Article(
                                tenant,
                                "Trends Article",
                                "key-" + System.nanoTime(),
                                "file.pdf",
                                "application/pdf"));
        User owner =
                userRepository.saveAndFlush(
                        new User("trends-citation-owner-" + System.nanoTime() + "@example.com"));
        Conversation conversation =
                conversationRepository.saveAndFlush(new Conversation(tenant, owner));
        Message message =
                messageRepository.saveAndFlush(
                        new Message(conversation, MessageRole.ASSISTANT, "hi"));
        MessageArticleCitation citation =
                messageArticleCitationRepository.saveAndFlush(
                        new MessageArticleCitation(message, article));
        backdateCitation(citation, createdAt);
        return citation;
    }

    private void setUpStaffAdminActor(String email) {
        staffAdmin(email);
        authenticateAs(email);
        tenantContext.setStaffAdmin(true);
    }

    @Test
    void globalTrendsZeroFillsBothSeriesForSevenDays() {
        setUpStaffAdminActor("trends-zero-fill@example.com");

        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Trends Zero Fill Co"));
        backdateTenant(tenant, FIXED_NOW.minus(2, ChronoUnit.DAYS));

        GlobalTrendsDto result = globalMetricsService.globalTrends(MetricsPeriod.SEVEN_DAYS);

        assertThat(result.newTenantsPerDay()).hasSize(7);
        assertThat(result.newTenantsPerDay())
                .extracting(DailyCountDto::count)
                .contains(0L); // at least one zero-filled day with no new tenant
        assertThat(result.newTenantsPerDay().stream().mapToLong(DailyCountDto::count).sum())
                .isGreaterThanOrEqualTo(1L);
        assertThat(result.articlesReadPerDay()).hasSize(7);
        assertThat(result.articlesReadPerDay()).allMatch(day -> day.count() == 0L);
    }

    @Test
    void globalTrendsForAllReturnsOnlyDaysWithRowsSortedChronologically() {
        setUpStaffAdminActor("trends-all-days@example.com");

        Tenant older = tenantRepository.saveAndFlush(new Tenant("Trends All Older Co"));
        backdateTenant(older, FIXED_NOW.minus(40, ChronoUnit.DAYS));
        Tenant newer = tenantRepository.saveAndFlush(new Tenant("Trends All Newer Co"));
        backdateTenant(newer, FIXED_NOW.minus(10, ChronoUnit.DAYS));

        GlobalTrendsDto result = globalMetricsService.globalTrends(MetricsPeriod.ALL);

        assertThat(result.newTenantsPerDay()).allMatch(day -> day.count() > 0);
        assertThat(result.newTenantsPerDay())
                .isSortedAccordingTo(java.util.Comparator.comparing(DailyCountDto::date));
    }

    @Test
    void globalTrendsComputesPercentChangeAcrossCases() {
        setUpStaffAdminActor("trends-percent-change@example.com");

        Instant currentStart = FIXED_NOW.minus(30, ChronoUnit.DAYS);
        Instant previousStart = currentStart.minus(30, ChronoUnit.DAYS);

        // current > previous: 2 tenants in current window, 1 in previous window.
        Tenant currentA = tenantRepository.saveAndFlush(new Tenant("Trends Current A Co"));
        backdateTenant(currentA, currentStart.plus(1, ChronoUnit.HOURS));
        Tenant currentB = tenantRepository.saveAndFlush(new Tenant("Trends Current B Co"));
        backdateTenant(currentB, currentStart.plus(2, ChronoUnit.HOURS));
        Tenant previousA = tenantRepository.saveAndFlush(new Tenant("Trends Previous A Co"));
        backdateTenant(previousA, previousStart.plus(1, ChronoUnit.HOURS));

        GlobalTrendsDto result = globalMetricsService.globalTrends(MetricsPeriod.THIRTY_DAYS);

        assertThat(result.totalTenants().current()).isGreaterThanOrEqualTo(2);
        assertThat(result.totalTenants().previous()).isNotNull();
        assertThat(result.totalTenants().percentChange()).isNotNull();
    }

    @Test
    void globalTrendsReturnsNullPreviousAndPercentChangeForAllPeriod() {
        setUpStaffAdminActor("trends-all-null-comparison@example.com");

        GlobalTrendsDto result = globalMetricsService.globalTrends(MetricsPeriod.ALL);

        assertThat(result.totalTenants().previous()).isNull();
        assertThat(result.totalTenants().percentChange()).isNull();
        assertThat(result.newTenants().previous()).isNull();
        assertThat(result.newTenants().percentChange()).isNull();
        assertThat(result.totalArticlesRead().previous()).isNull();
        assertThat(result.totalArticlesRead().percentChange()).isNull();
        assertThat(result.staffCount().previous()).isNull();
        assertThat(result.staffCount().percentChange()).isNull();
    }

    @Test
    void globalTrendsReturnsNullPercentChangeWhenPreviousWindowIsZeroAndCurrentIsPositive() {
        setUpStaffAdminActor("trends-zero-previous@example.com");

        Instant currentStart = FIXED_NOW.minus(7, ChronoUnit.DAYS);
        citationAt(currentStart.plus(1, ChronoUnit.HOURS));

        GlobalTrendsDto result = globalMetricsService.globalTrends(MetricsPeriod.SEVEN_DAYS);

        assertThat(result.totalArticlesRead().current()).isGreaterThan(0);
        assertThat(result.totalArticlesRead().previous()).isZero();
        assertThat(result.totalArticlesRead().percentChange()).isNull();
    }

    @Test
    void previousWindowStartComputesNonOverlappingBoundsForBoundedPeriods() {
        Instant currentStart = FIXED_NOW.minus(30, ChronoUnit.DAYS);

        Instant sevenDaysPrevious =
                globalMetricsService.previousWindowStart(
                        MetricsPeriod.SEVEN_DAYS,
                        currentStart,
                        Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        Instant thirtyDaysPrevious =
                globalMetricsService.previousWindowStart(
                        MetricsPeriod.THIRTY_DAYS,
                        currentStart,
                        Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        Instant ninetyDaysPrevious =
                globalMetricsService.previousWindowStart(
                        MetricsPeriod.NINETY_DAYS,
                        currentStart,
                        Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        assertThat(sevenDaysPrevious).isEqualTo(currentStart.minus(7, ChronoUnit.DAYS));
        assertThat(thirtyDaysPrevious).isEqualTo(currentStart.minus(30, ChronoUnit.DAYS));
        assertThat(ninetyDaysPrevious).isEqualTo(currentStart.minus(90, ChronoUnit.DAYS));
    }

    // --- specify/features/global-staff-dashboard-sparklines/SPEC.md REQ-1/2/6: globalTrends wires
    // the two new cumulative series through mergeCarryForwardDays, and existing fields are
    // unchanged ---

    @Test
    void globalTrendsWiresCumulativeTotalTenantsAndStaffCountPerDay() {
        setUpStaffAdminActor("sparklines-wiring@example.com");

        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Sparklines Wiring Co"));
        backdateTenant(tenant, FIXED_NOW.minus(3, ChronoUnit.DAYS));

        User extraStaff = staff("sparklines-wiring-staff@example.com");
        backdateUser(extraStaff, FIXED_NOW.minus(3, ChronoUnit.DAYS));

        GlobalTrendsDto result = globalMetricsService.globalTrends(MetricsPeriod.SEVEN_DAYS);

        assertThat(result.totalTenantsPerDay()).hasSize(7);
        assertThat(result.staffCountPerDay()).hasSize(7);
        // Cumulative series must never be zero on the last (today) day once at least one tenant
        // exists.
        assertThat(result.totalTenantsPerDay().get(6).count()).isGreaterThan(0);
        assertThat(result.staffCountPerDay().get(6).count()).isGreaterThan(0);
        // Never decreasing day over day.
        long previousTotalTenants = Long.MIN_VALUE;
        for (DailyCountDto day : result.totalTenantsPerDay()) {
            assertThat(day.count()).isGreaterThanOrEqualTo(previousTotalTenants);
            previousTotalTenants = day.count();
        }
        long previousStaffCount = Long.MIN_VALUE;
        for (DailyCountDto day : result.staffCountPerDay()) {
            assertThat(day.count()).isGreaterThanOrEqualTo(previousStaffCount);
            previousStaffCount = day.count();
        }
    }

    @Test
    void globalTrendsLeavesExistingFieldsUnchangedAfterAddingCumulativeSeries() {
        setUpStaffAdminActor("sparklines-regression@example.com");

        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Sparklines Regression Co"));
        backdateTenant(tenant, FIXED_NOW.minus(2, ChronoUnit.DAYS));

        GlobalTrendsDto result = globalMetricsService.globalTrends(MetricsPeriod.SEVEN_DAYS);

        assertThat(result.newTenantsPerDay()).hasSize(7);
        assertThat(result.articlesReadPerDay()).hasSize(7);
        assertThat(result.totalTenants()).isNotNull();
        assertThat(result.newTenants()).isNotNull();
        assertThat(result.totalArticlesRead()).isNotNull();
        assertThat(result.staffCount()).isNotNull();
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
