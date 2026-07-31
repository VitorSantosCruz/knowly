package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
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
 * specify/features/staff-user-listing/SPEC.md REQ-1..REQ-6: {@code GET /api/staff/users} lists
 * every {@code STAFF}/{@code STAFF_ADMIN} user (id/email/globalRole), optionally filtered by an
 * {@code email} substring, gated by the new, ceiling-independent {@link
 * GlobalPermission#STAFF_USER_VIEW}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffUserListingIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private LoginCodeService loginCodeService;
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

    // Same reasoning as StaffServiceCeilingIntegrationTest/AuthControllerIntegrationTest: uses the
    // real cookie-issuance flow rather than SecurityMockMvcRequestPostProcessors.csrf(), which
    // would corrupt the shared CsrfFilter bean's tokenRepository for the rest of this class's
    // tests.
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

    private User staff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        return userRepository.saveAndFlush(user);
    }

    // --- REQ-1/REQ-3: STAFF_ADMIN sees every STAFF/STAFF_ADMIN row, unconditionally ---

    @Test
    void staffAdminListsEveryStaffAndStaffAdminUserRegardlessOfGrants() {
        User actor = staffAdmin("listing-admin-actor@example.com");
        User otherStaff = staff("listing-admin-sees-staff@example.com");
        User otherAdmin = staffAdmin("listing-admin-sees-admin@example.com");
        Cookie session = logIn("listing-admin-actor@example.com");

        var response = mockMvc.get().uri("/api/staff/users").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response)
                .bodyJson()
                .extractingPath("$[*].email")
                .asInstanceOf(LIST)
                .contains(actor.getEmail(), otherStaff.getEmail(), otherAdmin.getEmail());
    }

    // --- REQ-2: email substring filter, case-insensitive ---

    @Test
    void staffAdminFiltersListByEmailSubstringCaseInsensitively() {
        staffAdmin("listing-filter-actor@example.com");
        User match = staff("Listing-Filter-Match@example.com");
        staff("listing-filter-nomatch@example.com");
        Cookie session = logIn("listing-filter-actor@example.com");

        var response =
                mockMvc.get().uri("/api/staff/users?email=filter-match").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response)
                .bodyJson()
                .extractingPath("$[*].email")
                .asInstanceOf(LIST)
                .containsExactly(match.getEmail());
    }

    // --- Response shape: id/email/globalRole per row, no extra permission/access-group detail
    // (Non-functional requirements: "Data exposure") ---

    @Test
    void listResponseIncludesOnlyIdEmailAndGlobalRolePerUser() {
        User actor = staffAdmin("listing-shape-actor@example.com");
        User otherAdmin = staffAdmin("listing-shape-admin@example.com");
        Cookie session = logIn("listing-shape-actor@example.com");

        var response = mockMvc.get().uri("/api/staff/users").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response)
                .bodyJson()
                .extractingPath("$[?(@.email=='" + otherAdmin.getEmail() + "')].id")
                .asInstanceOf(LIST)
                .containsExactly(otherAdmin.getId().intValue());
        assertThat(response)
                .bodyJson()
                .extractingPath("$[?(@.email=='" + otherAdmin.getEmail() + "')].globalRole")
                .asInstanceOf(LIST)
                .containsExactly("STAFF_ADMIN");
        assertThat(response)
                .bodyJson()
                .extractingPath("$[?(@.email=='" + actor.getEmail() + "')].globalRole")
                .asInstanceOf(LIST)
                .containsExactly("STAFF_ADMIN");
    }

    // --- REQ-2 edge case: a blank ?email= is treated as "no filter", not "match nothing" ---

    @Test
    void blankEmailQueryParamIsTreatedAsNoFilter() {
        staffAdmin("listing-blank-filter-actor@example.com");
        User otherStaff = staff("listing-blank-filter-sees-staff@example.com");
        Cookie session = logIn("listing-blank-filter-actor@example.com");

        var response = mockMvc.get().uri("/api/staff/users?email=").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response)
                .bodyJson()
                .extractingPath("$[*].email")
                .asInstanceOf(LIST)
                .contains(otherStaff.getEmail());
    }

    // --- REQ-2 edge case: an email filter matching nothing returns an empty list, not an error
    // ---

    @Test
    void emailFilterMatchingNoUserReturnsEmptyList() {
        staffAdmin("listing-empty-result-actor@example.com");
        Cookie session = logIn("listing-empty-result-actor@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/staff/users?email=no-such-user-anywhere-xyz")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response).bodyJson().extractingPath("$").asInstanceOf(LIST).isEmpty();
    }

    // --- REQ-5: STAFF caller with zero grants is rejected ---

    @Test
    void staffUserWithoutGrantIsRejectedFromListing() {
        staff("listing-no-grant@example.com");
        Cookie session = logIn("listing-no-grant@example.com");

        var response = mockMvc.get().uri("/api/staff/users").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    // --- REQ-4: STAFF holding STAFF_USER_VIEW succeeds and sees other STAFF/STAFF_ADMIN rows ---

    @Test
    void staffUserWithStaffUserViewGrantCanListOtherStaffAndStaffAdminUsers() {
        User actor = staff("listing-granted@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(actor, GlobalPermission.STAFF_USER_VIEW));
        User otherStaff = staff("listing-granted-sees-staff@example.com");
        User otherAdmin = staffAdmin("listing-granted-sees-admin@example.com");
        Cookie session = logIn("listing-granted@example.com");

        var response = mockMvc.get().uri("/api/staff/users").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response)
                .bodyJson()
                .extractingPath("$[*].email")
                .asInstanceOf(LIST)
                .contains(otherStaff.getEmail(), otherAdmin.getEmail());
    }

    // --- REQ-6: same STAFF_USER_VIEW-holding STAFF caller is still rejected from managing a
    // STAFF/STAFF_ADMIN target, proving listing and management authorization are independent ---

    @Test
    void staffUserWithStaffUserViewGrantIsStillRejectedManagingAStaffTarget() {
        User actor = staff("listing-granted-ceiling@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(actor, GlobalPermission.STAFF_USER_VIEW));
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(actor, GlobalPermission.STAFF_PERMISSION_MANAGE));
        User target = staff("listing-granted-ceiling-target@example.com");
        Cookie session = logIn("listing-granted-ceiling@example.com");
        Cookie csrfCookie = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/staff/users/" + target.getId() + "/permissions")
                        .cookie(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permission\":\"TENANT_CREATE\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }
}
