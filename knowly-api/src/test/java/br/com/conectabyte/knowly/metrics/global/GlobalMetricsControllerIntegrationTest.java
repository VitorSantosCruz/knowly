package br.com.conectabyte.knowly.metrics.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
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
}
