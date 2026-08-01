package br.com.conectabyte.knowly.metrics.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * specify/features/global-staff-dashboard-metrics/SPEC.md acceptance criteria: {@code GET
 * /api/staff/metrics/global} returns the four counts for a caller holding {@code
 * DASHBOARD_VIEW_GLOBAL} (or {@code STAFF_ADMIN}), and 403 otherwise.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalMetricsControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuditEventRepository auditEventRepository;
    @MockitoBean private JavaMailSender mailSender;

    private Cookie logIn(String email) {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        String code = loginCodeService.generate(email);
        var result =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}")
                        .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        return result.getResponse().getCookie("SESSION");
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

    // --- 200 for STAFF_ADMIN ---

    @Test
    void staffAdminGetsGlobalMetrics() {
        staffAdmin("global-metrics-controller-admin@example.com");
        Cookie session = logIn("global-metrics-controller-admin@example.com");

        var response = mockMvc.get().uri("/api/staff/metrics/global").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response).bodyJson().extractingPath("$.tenantCount").isNotNull();
        assertThat(response).bodyJson().extractingPath("$.newTenantsThisMonth").isNotNull();
        assertThat(response).bodyJson().extractingPath("$.articlesReadTotal").isNotNull();
        assertThat(response).bodyJson().extractingPath("$.staffCount").isNotNull();
    }

    // --- 200 for STAFF holding the grant ---

    @Test
    void staffWithGrantGetsGlobalMetrics() {
        User actor = staff("global-metrics-controller-granted@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(actor, GlobalPermission.DASHBOARD_VIEW_GLOBAL));
        Cookie session = logIn("global-metrics-controller-granted@example.com");

        var response = mockMvc.get().uri("/api/staff/metrics/global").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    // --- 403 for STAFF without the grant ---

    @Test
    void staffWithoutGrantIsRejected() {
        staff("global-metrics-controller-no-grant@example.com");
        Cookie session = logIn("global-metrics-controller-no-grant@example.com");

        var response = mockMvc.get().uri("/api/staff/metrics/global").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    // --- 403 for a tenant MEMBER/MEMBER_ADMIN with no GlobalRole ---

    @Test
    void tenantMemberWithNoGlobalRoleIsRejected() {
        User user =
                userRepository.saveAndFlush(
                        new User("global-metrics-controller-member@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Global Metrics Member Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie session = logIn("global-metrics-controller-member@example.com");

        var response = mockMvc.get().uri("/api/staff/metrics/global").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    // --- "new tenants this month" boundary: previous-UTC-month excluded, current-UTC-month
    // included ---

    @Test
    void newTenantsThisMonthExcludesPreviousMonthAndIncludesCurrentMonth() throws Exception {
        staffAdmin("global-metrics-controller-boundary@example.com");
        Cookie session = logIn("global-metrics-controller-boundary@example.com");

        Instant startOfCurrentUtcMonth =
                java.time.LocalDate.now(ZoneOffset.UTC)
                        .withDayOfMonth(1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();

        var before = mockMvc.get().uri("/api/staff/metrics/global").cookie(session).exchange();
        assertThat(before).hasStatus(HttpStatus.OK);
        long baseline =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(before.getResponse().getContentAsString())
                        .get("newTenantsThisMonth")
                        .asLong();

        Tenant currentMonthTenant =
                tenantRepository.saveAndFlush(new Tenant("Boundary Current Month Co"));
        backdateTenant(currentMonthTenant, startOfCurrentUtcMonth.plus(1, ChronoUnit.DAYS));

        Tenant previousMonthTenant =
                tenantRepository.saveAndFlush(new Tenant("Boundary Previous Month Co"));
        backdateTenant(previousMonthTenant, startOfCurrentUtcMonth.minus(1, ChronoUnit.DAYS));

        var response = mockMvc.get().uri("/api/staff/metrics/global").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        long updated =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(response.getResponse().getContentAsString())
                        .get("newTenantsThisMonth")
                        .asLong();

        assertThat(updated).isEqualTo(baseline + 1);
    }

    // --- specify/features/global-staff-dashboard-trends/SPEC.md REQ-1/7/8/9/10/11 ---

    @Test
    void staffAdminGetsGlobalTrendsForEveryPeriodAndDefault() {
        staffAdmin("global-trends-controller-admin@example.com");
        Cookie session = logIn("global-trends-controller-admin@example.com");

        for (String period : new String[] {"7d", "30d", "90d", "all", null}) {
            var request = mockMvc.get().uri("/api/staff/metrics/global/trends").cookie(session);
            var response = (period == null ? request : request.param("period", period)).exchange();

            assertThat(response).hasStatus(HttpStatus.OK);
            assertThat(response).bodyJson().extractingPath("$.newTenantsPerDay").isNotNull();
            assertThat(response).bodyJson().extractingPath("$.articlesReadPerDay").isNotNull();
            assertThat(response).bodyJson().extractingPath("$.totalTenants.current").isNotNull();
            assertThat(response).bodyJson().extractingPath("$.newTenants.current").isNotNull();
            assertThat(response)
                    .bodyJson()
                    .extractingPath("$.totalArticlesRead.current")
                    .isNotNull();
            assertThat(response).bodyJson().extractingPath("$.staffCount.current").isNotNull();
            assertThat(response).bodyJson().extractingPath("$.totalTenantsPerDay").isNotNull();
            assertThat(response).bodyJson().extractingPath("$.staffCountPerDay").isNotNull();
        }
    }

    // --- specify/features/global-staff-dashboard-sparklines/SPEC.md REQ-1/2/3/7: cumulative
    // carry-forward series at the API boundary ---

    @Test
    void globalTrendsReturnsCumulativeCarryForwardSeriesWithSameShapeAsExistingSeries()
            throws Exception {
        staffAdmin("sparklines-controller-admin@example.com");
        Cookie session = logIn("sparklines-controller-admin@example.com");

        Instant beforeWindow = Instant.now().minus(40, ChronoUnit.DAYS);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Sparklines Controller Co"));
        backdateTenant(tenant, beforeWindow);

        var response =
                mockMvc.get()
                        .uri("/api/staff/metrics/global/trends")
                        .param("period", "7d")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        var body =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(response.getResponse().getContentAsString());
        var totalTenantsPerDay = body.get("totalTenantsPerDay");
        var staffCountPerDay = body.get("staffCountPerDay");

        assertThat(totalTenantsPerDay.size()).isEqualTo(7);
        assertThat(staffCountPerDay.size()).isEqualTo(7);
        assertThat(totalTenantsPerDay.get(0).has("date")).isTrue();
        assertThat(totalTenantsPerDay.get(0).has("count")).isTrue();

        // The tenant created well before the 7d window must still be carried forward on day 1,
        // never zero-filled, since it existed as of that day's running total.
        assertThat(totalTenantsPerDay.get(0).get("count").asLong()).isGreaterThan(0);
    }

    @Test
    void staffWithGrantGetsGlobalTrendsAndWithoutGrantIsRejected() {
        User granted = staff("global-trends-controller-granted@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(granted, GlobalPermission.DASHBOARD_VIEW_GLOBAL));
        Cookie grantedSession = logIn("global-trends-controller-granted@example.com");

        var grantedResponse =
                mockMvc.get()
                        .uri("/api/staff/metrics/global/trends")
                        .cookie(grantedSession)
                        .exchange();
        assertThat(grantedResponse).hasStatus(HttpStatus.OK);

        staff("global-trends-controller-no-grant@example.com");
        Cookie noGrantSession = logIn("global-trends-controller-no-grant@example.com");

        var noGrantResponse =
                mockMvc.get()
                        .uri("/api/staff/metrics/global/trends")
                        .cookie(noGrantSession)
                        .exchange();
        assertThat(noGrantResponse).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantMemberWithNoGlobalRoleIsRejectedForGlobalTrends() {
        User user =
                userRepository.saveAndFlush(
                        new User("global-trends-controller-member@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Global Trends Member Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie session = logIn("global-trends-controller-member@example.com");

        var response =
                mockMvc.get().uri("/api/staff/metrics/global/trends").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidPeriodGetsBadRequestForGlobalTrends() {
        staffAdmin("global-trends-controller-invalid-period@example.com");
        Cookie session = logIn("global-trends-controller-invalid-period@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/metrics/global/trends")
                        .param("period", "not-a-real-period")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void globalTrendsReflectsAllTenantsNotJustOne() throws Exception {
        staffAdmin("global-trends-controller-cross-tenant@example.com");
        Cookie session = logIn("global-trends-controller-cross-tenant@example.com");

        var before =
                mockMvc.get().uri("/api/staff/metrics/global/trends").cookie(session).exchange();
        assertThat(before).hasStatus(HttpStatus.OK);
        long baseline =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(before.getResponse().getContentAsString())
                        .get("totalTenants")
                        .get("current")
                        .asLong();

        tenantRepository.saveAndFlush(new Tenant("Global Trends Cross Tenant A"));
        tenantRepository.saveAndFlush(new Tenant("Global Trends Cross Tenant B"));

        var response =
                mockMvc.get().uri("/api/staff/metrics/global/trends").cookie(session).exchange();
        assertThat(response).hasStatus(HttpStatus.OK);
        long updated =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(response.getResponse().getContentAsString())
                        .get("totalTenants")
                        .get("current")
                        .asLong();

        assertThat(updated).isEqualTo(baseline + 2);
    }

    @Test
    void globalTrendsProducesAnAuditEvent() {
        User actor = staffAdmin("global-trends-controller-audit@example.com");
        Cookie session = logIn("global-trends-controller-audit@example.com");

        var response =
                mockMvc.get().uri("/api/staff/metrics/global/trends").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(actor.getId());
        assertThat(events).extracting(AuditEvent::getAction).contains("metrics.global.trends.view");
    }
}
