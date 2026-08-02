package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.OneTimePasswordService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.util.List;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffUserProvisioningIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private OneTimePasswordService oneTimePasswordService;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
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

    // /api/staff/** is not in SecurityConfig's CSRF-exemption list (only pre-authentication and
    // legacy /api/tenants/** endpoints are), so every POST here needs a real CSRF cookie/header.
    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
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

    @Test
    void staffAdminCreatesANewStaffUserWithNoPermissionsAndSendsAnEmail() throws Exception {
        staffAdmin("admin@example.com");
        Cookie session = logIn("admin@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        Cookie csrfCookie = obtainCsrfCookie();
        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"new-staff@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
        assertThat(response.getResponse().getContentAsString())
                .contains("\"effectivePermissions\":[]");

        User created = userRepository.findByEmailIgnoreCase("new-staff@example.com").orElseThrow();
        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF);
        // 2 sends expected: verifyCode's own OTP-refresh mail (for the admin logging in) plus the
        // one this feature adds for the newly created staff user.
        verify(mailSender, org.mockito.Mockito.times(2)).send((MimeMessage) any());
    }

    // Per role-model-refinement (SPEC Decision 2), createStaffUser was narrowed to be
    // STAFF_ADMIN-only: an ungranted STAFF is (still) rejected, but a STAFF holding
    // STAFF_USER_CREATE is *also* now rejected (that grant alone no longer suffices),
    // and only STAFF_ADMIN succeeds.
    @Test
    void ungrantedStaffCannotCreateAStaffUser() {
        limitedStaff("nogrant@example.com");
        Cookie noGrantSession = logIn("nogrant@example.com");
        Cookie csrfCookie1 = obtainCsrfCookie();

        var rejected =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(noGrantSession)
                        .cookie(csrfCookie1)
                        .header("X-XSRF-TOKEN", csrfCookie1.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"someone@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();
        assertThat(rejected).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffGrantedStaffUserCreateCannotCreateAStaffUserButStaffAdminCan() {
        User granted = limitedStaff("granted@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(granted, GlobalPermission.STAFF_USER_CREATE));
        Cookie grantedSession = logIn("granted@example.com");
        Cookie csrfCookie1 = obtainCsrfCookie();

        var rejected =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(grantedSession)
                        .cookie(csrfCookie1)
                        .header("X-XSRF-TOKEN", csrfCookie1.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"someone@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();
        assertThat(rejected).hasStatus(HttpStatus.FORBIDDEN);

        staffAdmin("admin-provisioning@example.com");
        Cookie adminSession = logIn("admin-provisioning@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        Cookie csrfCookie2 = obtainCsrfCookie();

        var allowed =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(adminSession)
                        .cookie(csrfCookie2)
                        .header("X-XSRF-TOKEN", csrfCookie2.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"someone@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();
        assertThat(allowed).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void staffPermissionManageAloneDoesNotGrantStaffUserCreate() {
        User user = limitedStaff("permmanager@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(user, GlobalPermission.STAFF_PERMISSION_MANAGE));
        Cookie session = logIn("permmanager@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"someone-else@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void creatingAStaffUserWithAnExistingEmailIsRejected() throws Exception {
        staffAdmin("admin2@example.com");
        userRepository.saveAndFlush(new User("existing@example.com"));
        Cookie session = logIn("admin2@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"existing@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
        assertThat(response.getResponse().getContentAsString())
                .contains("STAFF_USER_ALREADY_EXISTS");
    }

    @Test
    void newlyCreatedStaffUserCanLogInViaOneTimePasswordAndViaLoginCode() {
        staffAdmin("admin3@example.com");
        Cookie session = logIn("admin3@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        Cookie csrfCookie = obtainCsrfCookie();

        var createResponse =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"newbie@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();
        assertThat(createResponse).hasStatus(HttpStatus.CREATED);

        User newbie = userRepository.findByEmailIgnoreCase("newbie@example.com").orElseThrow();
        String password = oneTimePasswordService.generateFor(newbie);

        var passwordLoginResponse =
                mockMvc.post()
                        .uri("/api/auth/login-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"newbie@example.com\",\"password\":\""
                                        + password
                                        + "\"}")
                        .exchange();
        assertThat(passwordLoginResponse).hasStatus(HttpStatus.OK);

        String code = loginCodeService.generate("newbie@example.com");
        var codeLoginResponse =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newbie@example.com\",\"code\":\"" + code + "\"}")
                        .exchange();
        assertThat(codeLoginResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void creatingAStaffUserEmitsAnAuditEvent() {
        User admin = staffAdmin("admin4@example.com");
        Cookie session = logIn("admin4@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"audited@example.com\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();
        assertThat(response).hasStatus(HttpStatus.CREATED);

        List<br.com.conectabyte.knowly.audit.AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(admin.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getAction()).isEqualTo("staff.user.create");
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    // user-role-selection-at-creation: REQ-2/REQ-3/REQ-5 end-to-end.

    @Test
    void staffAdminCreatesANewStaffUserWithRoleStaffAdminAndEmitsAuditEventWithTheRole() {
        User admin = staffAdmin("admin-role-select@example.com");
        Cookie session = logIn("admin-role-select@example.com");
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"new-staffadmin-e2e@example.com\",\"role\":\"STAFF_ADMIN\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);

        User created =
                userRepository
                        .findByEmailIgnoreCase("new-staffadmin-e2e@example.com")
                        .orElseThrow();
        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF_ADMIN);

        List<br.com.conectabyte.knowly.audit.AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(admin.getId());
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getAction()).isEqualTo("staff.user.create");
                            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
                            assertThat(event.getMetadata()).contains("STAFF_ADMIN");
                        });
    }

    @Test
    void aStaffCallerRequestingRoleStaffAdminIsRejectedAndCreatesNoUser() {
        limitedStaff("staff-role-select@example.com");
        Cookie session = logIn("staff-role-select@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"rejected-staffadmin-e2e@example.com\",\"role\":\"STAFF_ADMIN\",\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\",\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\",\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(userRepository.findByEmailIgnoreCase("rejected-staffadmin-e2e@example.com"))
                .isEmpty();
    }
}
