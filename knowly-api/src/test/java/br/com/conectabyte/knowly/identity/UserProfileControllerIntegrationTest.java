package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
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
 * {@code /api/users/{me|id}/profile}, per specify/features/identity-profile-model/PLAN.md's API
 * contracts table.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
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
    void getOwnProfileSucceeds() throws Exception {
        userRepository.saveAndFlush(new User("controller-own-profile@example.com"));
        Cookie session = logIn("controller-own-profile@example.com");

        var response = mockMvc.get().uri("/api/users/me/profile").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString())
                .contains("controller-own-profile@example.com");
    }

    @Test
    void viewingAnotherUsersProfileWithoutTheApplicableRightIsForbidden() {
        userRepository.saveAndFlush(new User("controller-view-caller@example.com"));
        User target = userRepository.saveAndFlush(new User("controller-view-target@example.com"));
        Cookie session = logIn("controller-view-caller@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/users/" + target.getId() + "/profile")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aStaffAdminCanDirectlyEditAnyUsersProfile() throws Exception {
        User staffAdmin =
                userRepository.saveAndFlush(new User("controller-edit-staffadmin@example.com"));
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User target = userRepository.saveAndFlush(new User("controller-edit-target@example.com"));
        Cookie session = logIn("controller-edit-staffadmin@example.com");

        var response =
                mockMvc.put()
                        .uri("/api/users/" + target.getId() + "/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fullName\":\"Edited Name\",\"address\":null,\"rg\":null,\"cpf\":null,\"phone\":null}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("Edited Name");
    }

    @Test
    void aTenantProfileEditHolderIsForbiddenEditingThemselvesDirectly() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Controller Edit Co"));
        User holder = userRepository.saveAndFlush(new User("controller-edit-holder@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(holder, tenant, MembershipRole.MEMBER));
        Cookie session = logIn("controller-edit-holder@example.com");

        var response =
                mockMvc.put()
                        .uri("/api/users/" + holder.getId() + "/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fullName\":\"Self Edit\",\"address\":null,\"rg\":null,\"cpf\":null,\"phone\":null}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void submittingASelfEditRequestSucceedsAndCreatesA201() throws Exception {
        userRepository.saveAndFlush(new User("controller-submit@example.com"));
        Cookie session = logIn("controller-submit@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/users/me/profile/edit-requests")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fullName\":\"Requested Name\",\"address\":null,\"rg\":null,\"cpf\":null,\"phone\":null}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
        assertThat(response.getResponse().getContentAsString())
                .contains("Requested Name")
                .contains("PENDING");
    }

    @Test
    void submittingASecondSelfEditRequestWhileOneIsPendingConflicts() {
        userRepository.saveAndFlush(new User("controller-submit-conflict@example.com"));
        Cookie session = logIn("controller-submit-conflict@example.com");
        mockMvc.post()
                .uri("/api/users/me/profile/edit-requests")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"fullName\":\"First\",\"address\":null,\"rg\":null,\"cpf\":null,\"phone\":null}")
                .exchange();

        var response =
                mockMvc.post()
                        .uri("/api/users/me/profile/edit-requests")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fullName\":\"Second\",\"address\":null,\"rg\":null,\"cpf\":null,\"phone\":null}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
    }
}
