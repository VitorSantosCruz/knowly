package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * specify/features/tenant-creation/SPEC.md acceptance criteria, end to end against {@code POST
 * /api/tenants}'s full company-identification + first-admin-atomic-creation contract. See
 * TenantManagementIntegrationTest for the pre-existing staff-only/first-admin-role coverage this
 * feature builds on (REQ-7, unmodified).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantCreationIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
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

    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    private String basePayload(
            String name,
            String legalName,
            String taxId,
            String country,
            String contactEmail,
            String adminEmail) {
        return "{"
                + "\"name\":\""
                + name
                + "\",\"legalName\":\""
                + legalName
                + "\",\"taxId\":\""
                + taxId
                + "\",\"country\":\""
                + country
                + "\",\"contactEmail\":\""
                + contactEmail
                + "\",\"contactPhone\":\"11999999999\","
                + "\"address\":{\"postalCode\":\"01000-000\",\"street\":\"Rua Um\",\"number\":\"1\","
                + "\"neighborhood\":\"Centro\",\"city\":\"Sao Paulo\",\"state\":\"SP\"},"
                + "\"adminEmail\":\""
                + adminEmail
                + "\",\"profile\":{\"fullName\":\"Test User\","
                + "\"taxId\":\"52998224725\",\"countryCode\":\"BR\","
                + "\"address\":{\"addressLine1\":\"Rua Um, 100\",\"addressLine2\":\"Centro\","
                + "\"city\":\"Sao Paulo\",\"stateRegion\":\"SP\",\"postalCode\":\"01000-000\","
                + "\"countryCode\":\"BR\"},"
                + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}"
                + "}";
    }

    @Test
    void fullyValidSubmissionSucceedsAndStoredDataMatchesEveryField() throws Exception {
        User staff = staffAdmin("full-valid@example.com");
        Cookie session = logIn("full-valid@example.com");
        Cookie csrf = obtainCsrfCookie();
        String taxId = "12345678000199";

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                basePayload(
                                        "Full Valid Co",
                                        "Full Valid Ltda",
                                        taxId,
                                        "BR",
                                        "contact@fullvalid.com",
                                        "admin-fullvalid@example.com"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);

        Tenant tenant =
                tenantRepository.findAll().stream()
                        .filter(t -> t.getTaxId().equals(taxId))
                        .findFirst()
                        .orElseThrow();
        assertThat(tenant.getName()).isEqualTo("Full Valid Co");
        assertThat(tenant.getLegalName()).isEqualTo("Full Valid Ltda");
        assertThat(tenant.getCountry()).isEqualTo("BR");
        assertThat(tenant.getContactEmail()).isEqualTo("contact@fullvalid.com");
        assertThat(tenant.getContactPhone()).isEqualTo("11999999999");
        assertThat(tenant.getPostalCode()).isEqualTo("01000-000");
        assertThat(tenant.getStreet()).isEqualTo("Rua Um");
        assertThat(tenant.getNumber()).isEqualTo("1");
        assertThat(tenant.getNeighborhood()).isEqualTo("Centro");
        assertThat(tenant.getCity()).isEqualTo("Sao Paulo");
        assertThat(tenant.getState()).isEqualTo("SP");

        User admin =
                userRepository.findByEmailIgnoreCase("admin-fullvalid@example.com").orElseThrow();
        var membership = tenantMembershipRepository.findByUserAndActiveTrue(admin);
        assertThat(membership).hasSize(1);
        assertThat(membership.get(0).getRole()).isEqualTo(MembershipRole.MEMBER_ADMIN);
        assertThat(membership.get(0).getTenant().getId()).isEqualTo(tenant.getId());

        var events = auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(staff.getId());
        assertThat(events).anySatisfy(e -> assertThat(e.getAction()).isEqualTo("tenant.create"));
    }

    @Test
    void missingMandatoryCompanyFieldIsRejectedWith400() {
        User staff = staffAdmin("missing-field@example.com");
        Cookie session = logIn("missing-field@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                basePayload(
                                        "Missing Field Co",
                                        "",
                                        "12345678000280",
                                        "BR",
                                        "contact@missingfield.com",
                                        "admin-missingfield@example.com"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findByEmailIgnoreCase("admin-missingfield@example.com"))
                .isEmpty();

        // REQ-8: a Bean Validation rejection happens before TenantService#createTenant's own
        // @AuditLog is ever entered -- CreationValidationAuditAdvice (same mechanism already used
        // for addMember/createStaffUser) picks up the slack, per this feature's own coverage of
        // "audited on both success and rejection".
        var events = auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(staff.getId());
        assertThat(events)
                .anySatisfy(e -> assertThat(e.getAction()).isEqualTo("tenant.create.denied"));
    }

    @Test
    void malformedContactEmailIsRejectedWith400() {
        staffAdmin("malformed-email@example.com");
        Cookie session = logIn("malformed-email@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                basePayload(
                                        "Malformed Email Co",
                                        "Malformed Email Ltda",
                                        "12345678000371",
                                        "BR",
                                        "not-an-email",
                                        "admin-malformedemail@example.com"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateTaxIdIsRejectedWith409AndCreatesNoRow() throws Exception {
        staffAdmin("dup-taxid@example.com");
        Cookie session = logIn("dup-taxid@example.com");
        Cookie csrf = obtainCsrfCookie();
        String taxId = "12345678000462";
        Tenant existing = new Tenant("Existing Tax Owner Dup");
        existing.setTaxId(taxId);
        tenantRepository.saveAndFlush(existing);

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                basePayload(
                                        "Dup Co",
                                        "Dup Ltda",
                                        taxId,
                                        "BR",
                                        "contact@dup.com",
                                        "admin-dup@example.com"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
        assertThat(response.getResponse().getContentAsString()).contains("TENANT_ALREADY_EXISTS");
        assertThat(userRepository.findByEmailIgnoreCase("admin-dup@example.com")).isEmpty();
    }

    @Test
    void brazilWithNon14DigitTaxIdIsRejectedWith400() {
        staffAdmin("bad-cnpj@example.com");
        Cookie session = logIn("bad-cnpj@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                basePayload(
                                        "Bad Cnpj Co",
                                        "Bad Cnpj Ltda",
                                        "123",
                                        "BR",
                                        "contact@badcnpj.com",
                                        "admin-badcnpj@example.com"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.findByEmailIgnoreCase("admin-badcnpj@example.com")).isEmpty();
    }

    @Test
    void nonBrazilCountryWithAnyNonEmptyTaxIdSucceeds() {
        staffAdmin("non-brazil@example.com");
        Cookie session = logIn("non-brazil@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                basePayload(
                                        "Non Brazil Co",
                                        "Non Brazil Ltda",
                                        "EIN-" + System.nanoTime(),
                                        "US",
                                        "contact@nonbrazil.com",
                                        "admin-nonbrazil@example.com"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(userRepository.findByEmailIgnoreCase("admin-nonbrazil@example.com")).isPresent();
    }

    @Test
    void nonStaffCallerIsRejectedWith403AndCreatesNoRow() {
        User user = userRepository.saveAndFlush(new User("non-staff@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Non Staff Owner Tenant"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));

        Cookie session = logIn("non-staff@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                basePayload(
                                        "Non Staff Co",
                                        "Non Staff Ltda",
                                        "12345678000553",
                                        "BR",
                                        "contact@nonstaff.com",
                                        "admin-nonstaff@example.com"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(userRepository.findByEmailIgnoreCase("admin-nonstaff@example.com")).isEmpty();
    }

    @Test
    void missingFirstAdminProfileFieldIsRejected400AndCreatesNoTenant() {
        staffAdmin("missing-profile-field@example.com");
        Cookie session = logIn("missing-profile-field@example.com");
        Cookie csrf = obtainCsrfCookie();
        String taxId = "12345678000644";

        String payload =
                "{\"name\":\"Missing Profile Co\",\"legalName\":\"Missing Profile Ltda\","
                        + "\"taxId\":\""
                        + taxId
                        + "\",\"country\":\"BR\",\"contactEmail\":\"contact@missingprofile.com\","
                        + "\"contactPhone\":\"11999999999\","
                        + "\"address\":{\"postalCode\":\"01000-000\",\"street\":\"Rua Um\","
                        + "\"number\":\"1\",\"neighborhood\":\"Centro\",\"city\":\"Sao Paulo\","
                        + "\"state\":\"SP\"},\"adminEmail\":\"admin-missingprofile@example.com\","
                        + "\"profile\":{\"taxId\":\"52998224725\",\"countryCode\":\"BR\","
                        + "\"address\":{\"addressLine1\":\"Rua Um, 100\",\"addressLine2\":\"Centro\","
                        + "\"city\":\"Sao Paulo\",\"stateRegion\":\"SP\",\"postalCode\":\"01000-000\","
                        + "\"countryCode\":\"BR\"},"
                        + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}";

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tenantRepository.findAll()).noneMatch(t -> t.getTaxId().equals(taxId));
        assertThat(userRepository.findByEmailIgnoreCase("admin-missingprofile@example.com"))
                .isEmpty();
    }

    @Test
    void anAlreadyExistingAdminEmailIsRejectedWith409AndCreatesNoTenant() {
        staffAdmin("existing-admin-email@example.com");
        userRepository.saveAndFlush(new User("already-admin@example.com"));
        Cookie session = logIn("existing-admin-email@example.com");
        Cookie csrf = obtainCsrfCookie();
        String taxId = "12345678000735";

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                basePayload(
                                        "Existing Admin Co",
                                        "Existing Admin Ltda",
                                        taxId,
                                        "BR",
                                        "contact@existingadmin.com",
                                        "already-admin@example.com"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
        assertThat(tenantRepository.findAll()).noneMatch(t -> t.getTaxId().equals(taxId));
    }
}
