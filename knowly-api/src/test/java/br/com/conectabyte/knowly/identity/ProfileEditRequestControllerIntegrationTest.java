package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
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
 * {@code /api/profile-edit-requests}, per specify/features/identity-profile-model/PLAN.md's API
 * contracts table.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileEditRequestControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private ProfileEditRequestRepository profileEditRequestRepository;
    @Autowired private UserProfileRepository userProfileRepository;
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
    void listingReturnsOnlyPendingRequestsTheCallerHoldsTheApplicableRightOver() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("List Requests Co"));
        User requester =
                userRepository.saveAndFlush(new User("list-requests-requester@example.com"));
        User admin = userRepository.saveAndFlush(new User("list-requests-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie requesterSession = logIn("list-requests-requester@example.com");
        mockMvc.post()
                .uri("/api/users/me/profile/edit-requests")
                .cookie(requesterSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"fields\":{\"fullName\":\"Listed Name\",\"taxId\":null,\"countryCode\":null,\"address\":null,\"contacts\":null},\"contactChanges\":[]}")
                .exchange();

        Cookie adminSession = logIn("list-requests-admin@example.com");
        var response =
                mockMvc.get().uri("/api/profile-edit-requests").cookie(adminSession).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("Listed Name");
    }

    @Test
    void listingReturnsTheProposedAddressForAPendingRequest() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("List Address Co"));
        User requester =
                userRepository.saveAndFlush(new User("list-address-requester@example.com"));
        User admin = userRepository.saveAndFlush(new User("list-address-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie requesterSession = logIn("list-address-requester@example.com");
        mockMvc.post()
                .uri("/api/users/me/profile/edit-requests")
                .cookie(requesterSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"fields\":{\"fullName\":\"Address Listed\",\"taxId\":null,"
                                + "\"countryCode\":null,"
                                + "\"address\":{\"addressLine1\":\"Av Paulista, 1000\","
                                + "\"addressLine2\":\"Bela Vista\",\"city\":\"São Paulo\","
                                + "\"stateRegion\":\"SP\",\"postalCode\":\"01310-000\","
                                + "\"countryCode\":\"BR\"},"
                                + "\"contacts\":null},\"contactChanges\":[]}")
                .exchange();

        Cookie adminSession = logIn("list-address-admin@example.com");
        var response =
                mockMvc.get().uri("/api/profile-edit-requests").cookie(adminSession).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("Av Paulista");
    }

    @Test
    void approvingAPendingRequestSucceeds() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Approve Controller Co"));
        User requester =
                userRepository.saveAndFlush(new User("approve-controller-requester@example.com"));
        User admin = userRepository.saveAndFlush(new User("approve-controller-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie requesterSession = logIn("approve-controller-requester@example.com");
        mockMvc.post()
                .uri("/api/users/me/profile/edit-requests")
                .cookie(requesterSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"fields\":{\"fullName\":\"Approve Me\",\"taxId\":null,\"countryCode\":null,\"address\":null,\"contacts\":null},\"contactChanges\":[]}")
                .exchange();
        Long requestId =
                profileEditRequestRepository
                        .findByRequesterAndStatus(requester, ProfileEditRequestStatus.PENDING)
                        .orElseThrow()
                        .getId();

        Cookie adminSession = logIn("approve-controller-admin@example.com");
        var response =
                mockMvc.post()
                        .uri("/api/profile-edit-requests/" + requestId + "/approve")
                        .cookie(adminSession)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(profileEditRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(ProfileEditRequestStatus.APPROVED);
    }

    @Test
    void rejectingAPendingRequestSucceeds() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Reject Controller Co"));
        User requester =
                userRepository.saveAndFlush(new User("reject-controller-requester@example.com"));
        User admin = userRepository.saveAndFlush(new User("reject-controller-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie requesterSession = logIn("reject-controller-requester@example.com");
        mockMvc.post()
                .uri("/api/users/me/profile/edit-requests")
                .cookie(requesterSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"fields\":{\"fullName\":\"Reject Me\",\"taxId\":null,\"countryCode\":null,\"address\":null,\"contacts\":null},\"contactChanges\":[]}")
                .exchange();
        Long requestId =
                profileEditRequestRepository
                        .findByRequesterAndStatus(requester, ProfileEditRequestStatus.PENDING)
                        .orElseThrow()
                        .getId();

        Cookie adminSession = logIn("reject-controller-admin@example.com");
        var response =
                mockMvc.post()
                        .uri("/api/profile-edit-requests/" + requestId + "/reject")
                        .cookie(adminSession)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(profileEditRequestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(ProfileEditRequestStatus.REJECTED);
    }

    @Test
    void approvingWithoutTheApplicableRightIsForbidden() {
        User requester =
                userRepository.saveAndFlush(new User("forbidden-controller-requester@example.com"));
        userRepository.saveAndFlush(new User("forbidden-controller-impostor@example.com"));
        Cookie requesterSession = logIn("forbidden-controller-requester@example.com");
        mockMvc.post()
                .uri("/api/users/me/profile/edit-requests")
                .cookie(requesterSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"fields\":{\"fullName\":\"No Right\",\"taxId\":null,\"countryCode\":null,\"address\":null,\"contacts\":null},\"contactChanges\":[]}")
                .exchange();
        Long requestId =
                profileEditRequestRepository
                        .findByRequesterAndStatus(requester, ProfileEditRequestStatus.PENDING)
                        .orElseThrow()
                        .getId();

        Cookie impostorSession = logIn("forbidden-controller-impostor@example.com");
        var response =
                mockMvc.post()
                        .uri("/api/profile-edit-requests/" + requestId + "/approve")
                        .cookie(impostorSession)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void listingIncludesTheRequesterNameAndEmail() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Requester Identity Co"));
        User requester =
                userRepository.saveAndFlush(new User("requester-identity-requester@example.com"));
        User admin = userRepository.saveAndFlush(new User("requester-identity-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        UserProfile profile =
                userProfileRepository
                        .findById(requester.getId())
                        .orElseGet(() -> new UserProfile(requester));
        profile.setFullName("Requester Full Name");
        userProfileRepository.saveAndFlush(profile);

        Cookie requesterSession = logIn("requester-identity-requester@example.com");
        mockMvc.post()
                .uri("/api/users/me/profile/edit-requests")
                .cookie(requesterSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"fields\":{\"fullName\":\"New Name\",\"taxId\":null,\"countryCode\":null,\"address\":null,\"contacts\":null},\"contactChanges\":[]}")
                .exchange();

        Cookie adminSession = logIn("requester-identity-admin@example.com");
        var response =
                mockMvc.get().uri("/api/profile-edit-requests").cookie(adminSession).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"requesterName\":\"Requester Full Name\"");
        assertThat(body)
                .contains("\"requesterEmail\":\"requester-identity-requester@example.com\"");
    }

    @Test
    void listingHasNullRequesterNameWhenTheRequesterHasNoFullNameYet() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("No Name Co"));
        User requester = userRepository.saveAndFlush(new User("no-name-requester@example.com"));
        User admin = userRepository.saveAndFlush(new User("no-name-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));

        Cookie requesterSession = logIn("no-name-requester@example.com");
        mockMvc.post()
                .uri("/api/users/me/profile/edit-requests")
                .cookie(requesterSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"fields\":{\"fullName\":\"New Name\",\"taxId\":null,\"countryCode\":null,\"address\":null,\"contacts\":null},\"contactChanges\":[]}")
                .exchange();

        Cookie adminSession = logIn("no-name-admin@example.com");
        var response =
                mockMvc.get().uri("/api/profile-edit-requests").cookie(adminSession).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"requesterName\":null");
        assertThat(body).contains("\"requesterEmail\":\"no-name-requester@example.com\"");
    }

    @Test
    void approvingAnUnknownRequestIsNotFound() {
        userRepository.saveAndFlush(new User("unknown-controller-caller@example.com"));
        Cookie session = logIn("unknown-controller-caller@example.com");

        var response =
                mockMvc.post()
                        .uri("/api/profile-edit-requests/999999/approve")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }
}
