package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * {@code /api/users/{me|id}/profile}, per specify/features/identity-profile-model-v2/PLAN.md's API
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

    private static final String FIELDS_JSON =
            "{\"fullName\":\"%s\",\"cpf\":null,\"rg\":null,\"rgOrgaoEmissor\":null,"
                    + "\"birthDate\":null,\"address\":null,\"contacts\":null}";

    private static final String DIRECT_EDIT_BODY =
            "{\"fields\":" + FIELDS_JSON + ",\"contactChanges\":[]}";

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
    void aStaffAdminCanDirectlyEditAnotherUsersProfileButNotTheirOwn() throws Exception {
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
                        .content(DIRECT_EDIT_BODY.formatted("Edited Name"))
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("Edited Name");

        var selfEditResponse =
                mockMvc.put()
                        .uri("/api/users/" + staffAdmin.getId() + "/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DIRECT_EDIT_BODY.formatted("Self Edit Attempt"))
                        .exchange();

        assertThat(selfEditResponse).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aDirectEditCanAlsoApplyContactChanges() throws Exception {
        User staffAdmin =
                userRepository.saveAndFlush(
                        new User("controller-edit-contacts-staffadmin@example.com"));
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User target =
                userRepository.saveAndFlush(
                        new User("controller-edit-contacts-target@example.com"));
        Cookie session = logIn("controller-edit-contacts-staffadmin@example.com");

        var response =
                mockMvc.put()
                        .uri("/api/users/" + target.getId() + "/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fields\":"
                                        + FIELDS_JSON.formatted("Contact Edited Name")
                                        + ",\"contactChanges\":[{\"action\":\"ADD\",\"contactId\":null,"
                                        + "\"type\":\"PHONE\",\"value\":\"+5511999999999\","
                                        + "\"label\":\"Mobile\",\"isPrimary\":true}]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("+5511999999999");
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
                        .content(DIRECT_EDIT_BODY.formatted("Self Edit"))
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
                                "{\"fields\":"
                                        + FIELDS_JSON.formatted("Requested Name")
                                        + ",\"contactChanges\":[]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
        assertThat(response.getResponse().getContentAsString())
                .contains("Requested Name")
                .contains("PENDING");
    }

    @Test
    void submittingASelfEditRequestWithAnAddressReturnsItInTheResponse() throws Exception {
        userRepository.saveAndFlush(new User("controller-submit-address@example.com"));
        Cookie session = logIn("controller-submit-address@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/users/me/profile/edit-requests")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fields\":{\"fullName\":\"Address Requester\",\"cpf\":null,"
                                        + "\"rg\":null,\"rgOrgaoEmissor\":null,\"birthDate\":null,"
                                        + "\"address\":{\"cep\":\"01310-000\",\"logradouro\":\"Av"
                                        + " Paulista\",\"numero\":\"1000\",\"complemento\":null,"
                                        + "\"bairro\":\"Bela Vista\",\"cidade\":\"São"
                                        + " Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},"
                                        + "\"contacts\":null},\"contactChanges\":[]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);
        assertThat(response.getResponse().getContentAsString()).contains("Av Paulista");
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
                        "{\"fields\":" + FIELDS_JSON.formatted("First") + ",\"contactChanges\":[]}")
                .exchange();

        var response =
                mockMvc.post()
                        .uri("/api/users/me/profile/edit-requests")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fields\":"
                                        + FIELDS_JSON.formatted("Second")
                                        + ",\"contactChanges\":[]}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void uploadingAnAvatarUpdatesTheOwnProfileAndReturnsItInTheResponse() throws Exception {
        userRepository.saveAndFlush(new User("controller-avatar@example.com"));
        Cookie session = logIn("controller-avatar@example.com");
        MockMultipartFile file =
                new MockMultipartFile("file", "avatar.png", "image/png", "fake-bytes".getBytes());

        var result =
                mockMvc.perform(
                        multipart("/api/users/me/profile/avatar").file(file).cookie(session));

        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    void uploadingAnUnsupportedContentTypeIsRejected() {
        userRepository.saveAndFlush(new User("controller-avatar-bad-type@example.com"));
        Cookie session = logIn("controller-avatar-bad-type@example.com");
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "not-an-image.txt", "text/plain", "not an image".getBytes());

        var result =
                mockMvc.perform(
                        multipart("/api/users/me/profile/avatar").file(file).cookie(session));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
