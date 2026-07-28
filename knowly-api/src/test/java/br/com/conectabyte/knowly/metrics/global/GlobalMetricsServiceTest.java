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

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
