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
 * specify/features/tenant-crud/SPEC.md REQ-1 through REQ-7 and the {@code MEMBER_ADMIN}-forbidden
 * acceptance criterion, end to end against {@code PATCH /api/tenants/{tenantId}}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantEditIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
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

    private User limitedStaff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        return userRepository.saveAndFlush(user);
    }

    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    @Test
    void staffAdminEditsEveryEditableFieldAndTaxIdStaysUnchanged() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Integration Co"));
        String originalTaxId = tenant.getTaxId();
        staffAdmin("edit-integ-staffadmin@example.com");
        Cookie session = logIn("edit-integ-staffadmin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.patch()
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"Renamed Integ Co\",\"legalName\":\"Renamed Integ"
                                        + " Ltda\",\"contactEmail\":\"new@renamed.com\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);

        Tenant persisted = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Renamed Integ Co");
        assertThat(persisted.getLegalName()).isEqualTo("Renamed Integ Ltda");
        assertThat(persisted.getContactEmail()).isEqualTo("new@renamed.com");
        assertThat(persisted.getTaxId()).isEqualTo(originalTaxId);
    }

    @Test
    void malformedContactEmailIsRejectedWith400AndAppliesNoChange() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Malformed Co"));
        String originalEmail = tenant.getContactEmail();
        staffAdmin("edit-integ-malformed@example.com");
        Cookie session = logIn("edit-integ-malformed@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.patch()
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactEmail\":\"not-an-email\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        Tenant persisted = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persisted.getContactEmail()).isEqualTo(originalEmail);
    }

    @Test
    void blankMandatoryFieldIsRejectedWith400AndAppliesNoChange() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Blank Field Co"));
        String originalName = tenant.getName();
        staffAdmin("edit-integ-blank@example.com");
        Cookie session = logIn("edit-integ-blank@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.patch()
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        Tenant persisted = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo(originalName);
    }

    @Test
    void staffGrantedOnlyTenantEditWithoutTenantViewIsDenied() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit No View Integ Co"));
        User staff = limitedStaff("edit-integ-no-view@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.TENANT_EDIT));
        Cookie session = logIn("edit-integ-no-view@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.patch()
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Should Not Apply\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffGrantedBothTenantEditAndTenantViewSucceeds() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Granted Integ Co"));
        User staff = limitedStaff("edit-integ-granted@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.TENANT_EDIT));
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.TENANT_VIEW));
        Cookie session = logIn("edit-integ-granted@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.patch()
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Granted Applied\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        Tenant persisted = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Granted Applied");
    }

    @Test
    void memberAdminOfTheirOwnTenantCannotEditIt() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Member Admin Co"));
        User memberAdmin =
                userRepository.saveAndFlush(new User("edit-integ-memberadmin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(memberAdmin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie session = logIn("edit-integ-memberadmin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.patch()
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Should Not Apply\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
        Tenant persisted = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Edit Member Admin Co");
    }

    @Test
    void editingANonExistentTenantReturns404() {
        staffAdmin("edit-integ-missing@example.com");
        Cookie session = logIn("edit-integ-missing@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.patch()
                        .uri("/api/tenants/{id}", Long.MAX_VALUE)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Irrelevant\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void editingASoftDeletedTenantReturns404() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Soft Deleted Integ Co"));
        tenant.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);
        staffAdmin("edit-integ-soft-deleted@example.com");
        Cookie session = logIn("edit-integ-soft-deleted@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.patch()
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Irrelevant\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void everyEditAttemptIsAuditLogged() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Audit Co"));
        User staff = staffAdmin("edit-integ-audit@example.com");
        Cookie session = logIn("edit-integ-audit@example.com");
        Cookie csrf = obtainCsrfCookie();

        mockMvc.patch()
                .uri("/api/tenants/{id}", tenant.getId())
                .cookie(session)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Audited Name\"}")
                .exchange();

        var events = auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(staff.getId());
        assertThat(events).anySatisfy(e -> assertThat(e.getAction()).isEqualTo("tenant.edit"));
    }
}
