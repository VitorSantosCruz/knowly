package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
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
 * REQ-2/REQ-3/REQ-4/REQ-10: the bootstrap {@code STAFF_ADMIN} (seeded by {@code
 * staff-bootstrap-user}'s V13 migration, per {@code
 * spring.flyway.placeholders.bootstrap-staff-email} -- {@code bootstrap-test@conectabyte.com} in
 * {@code application-test.yaml}) -- and only that account -- starts pending, blocking every
 * non-allowlisted endpoint with {@code 409 PROFILE_COMPLETION_REQUIRED} until every mandatory field
 * is submitted, per specify/features/mandatory-complete-profile/SPEC.md/PLAN.md.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileCompletionGateIntegrationTest {

    private static final String BOOTSTRAP_EMAIL = "bootstrap-test@conectabyte.com";

    @Autowired private MockMvcTester mockMvc;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private UserRepository userRepository;
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

    @Test
    void aPendingBootstrapAccountIsRejectedOnAnArbitraryStaffOnlyEndpoint() throws Exception {
        Cookie session = logIn(BOOTSTRAP_EMAIL);

        var response = mockMvc.get().uri("/api/staff/users").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
        assertThat(response.getResponse().getContentAsString())
                .contains("PROFILE_COMPLETION_REQUIRED");
    }

    @Test
    void aPendingBootstrapAccountIsRejectedOnATenantScopedEndpointBeforeTenantSelectionLogicRuns()
            throws Exception {
        Cookie session = logIn(BOOTSTRAP_EMAIL);

        var response = mockMvc.get().uri("/api/tenants/permissions").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
        assertThat(response.getResponse().getContentAsString())
                .contains("PROFILE_COMPLETION_REQUIRED");
    }

    @Test
    void ownProfileRemainsReachableForThePendingBootstrapAccount() {
        Cookie session = logIn(BOOTSTRAP_EMAIL);

        var profileResponse = mockMvc.get().uri("/api/users/me/profile").cookie(session).exchange();
        assertThat(profileResponse).hasStatus(HttpStatus.OK);
    }

    @Test
    void loginItselfIsUnaffectedByThePendingBootstrapAccountsGate() {
        Cookie session = logIn(BOOTSTRAP_EMAIL);

        assertThat(session).isNotNull();
    }

    @Test
    void anUnrelatedIncompleteAccountIsNeverGatedByThisFeature() {
        // REQ-10: the gate is scoped to the single bootstrap row by identity, not by a bare
        // "is this profile incomplete" check -- a pre-existing, unrelated incomplete account (the
        // norm for every account created before this feature shipped) must never be gated.
        userRepository.saveAndFlush(new User("unrelated-incomplete@example.com"));
        Cookie session = logIn("unrelated-incomplete@example.com");

        var response = mockMvc.get().uri("/api/tenants/memberships").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }
}
