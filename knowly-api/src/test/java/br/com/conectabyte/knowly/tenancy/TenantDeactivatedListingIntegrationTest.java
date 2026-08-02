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
 * specify/features/tenant-crud/SPEC.md REQ-19/REQ-20/REQ-21, end to end against {@code GET
 * /api/tenants} (now excluding soft-deleted tenants) and the new {@code GET
 * /api/tenants/deactivated}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantDeactivatedListingIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
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
    void staffAdminSeesOnlyDeactivatedTenantsWithDeletedAtPopulatedAndActiveListingExcludesIt()
            throws Exception {
        String marker = "IntegDeactivated" + System.nanoTime();
        tenantRepository.saveAndFlush(new Tenant(marker + "Active"));
        Tenant deleted = tenantRepository.saveAndFlush(new Tenant(marker + "Deleted"));
        deleted.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(deleted);

        staffAdmin("deactivated-listing-staffadmin@example.com");
        Cookie session = logIn("deactivated-listing-staffadmin@example.com");

        var deactivatedResponse =
                mockMvc.get()
                        .uri("/api/tenants/deactivated?search=" + marker)
                        .cookie(session)
                        .exchange();

        assertThat(deactivatedResponse).hasStatus(HttpStatus.OK);
        assertThat(deactivatedResponse.getResponse().getContentAsString())
                .contains(marker + "Deleted")
                .doesNotContain(marker + "Active");

        var activeResponse =
                mockMvc.get().uri("/api/tenants?search=" + marker).cookie(session).exchange();

        assertThat(activeResponse).hasStatus(HttpStatus.OK);
        assertThat(activeResponse.getResponse().getContentAsString())
                .contains(marker + "Active")
                .doesNotContain(marker + "Deleted");
    }

    @Test
    void staffWithOnlyTenantActAsAnyIsDeniedTheDeactivatedListing() {
        User staff = limitedStaff("deactivated-listing-actasany@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.TENANT_ACT_AS_ANY));
        Cookie session = logIn("deactivated-listing-actasany@example.com");

        var response = mockMvc.get().uri("/api/tenants/deactivated").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffGrantedTenantDeleteAndTenantViewSeesTheDeactivatedListing() throws Exception {
        String marker = "IntegDeactivatedGranted" + System.nanoTime();
        Tenant deleted = tenantRepository.saveAndFlush(new Tenant(marker));
        deleted.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(deleted);

        User staff = limitedStaff("deactivated-listing-granted@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.TENANT_DELETE));
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.TENANT_VIEW));
        Cookie session = logIn("deactivated-listing-granted@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/deactivated?search=" + marker)
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains(marker);
    }
}
