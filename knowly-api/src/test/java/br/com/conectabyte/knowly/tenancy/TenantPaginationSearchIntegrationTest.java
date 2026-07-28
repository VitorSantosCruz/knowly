package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
 * specify/features/tenant-pagination-search/SPEC.md acceptance criteria: {@code GET /api/tenants}
 * end-to-end contract (envelope shape, clamping, rejection, search, authorization).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantPaginationSearchIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
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
    void defaultEnvelopeShapeAndFieldNames() throws Exception {
        staffAdmin("default-envelope@example.com");
        tenantRepository.saveAndFlush(new Tenant("Envelope Co " + System.nanoTime()));
        Cookie session = logIn("default-envelope@example.com");

        var response = mockMvc.get().uri("/api/tenants").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.page")).isZero();
        assertThat((Integer) JsonPath.read(body, "$.size")).isEqualTo(20);
        assertThat((Number) JsonPath.read(body, "$.totalElements")).isNotNull();
        assertThat((Number) JsonPath.read(body, "$.totalPages")).isNotNull();
        assertThat((java.util.List<?>) JsonPath.read(body, "$.content")).isNotNull();
    }

    @Test
    void nextPageSlice() throws Exception {
        staffAdmin("next-page@example.com");
        String marker = "NextPage" + System.nanoTime();
        for (int i = 0; i < 10; i++) {
            tenantRepository.saveAndFlush(new Tenant(marker + i));
        }
        Cookie session = logIn("next-page@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants?page=1&size=5&search=" + marker.toLowerCase())
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.page")).isEqualTo(1);
        assertThat((java.util.List<?>) JsonPath.read(body, "$.content")).hasSize(5);
    }

    @Test
    void sizeOver100IsClampedNotRejected() throws Exception {
        staffAdmin("clamp-size@example.com");
        Cookie session = logIn("clamp-size@example.com");

        var response = mockMvc.get().uri("/api/tenants?size=500").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.size")).isEqualTo(100);
    }

    @Test
    void negativePageIsRejectedWith400() throws Exception {
        staffAdmin("neg-page@example.com");
        Cookie session = logIn("neg-page@example.com");

        var response = mockMvc.get().uri("/api/tenants?page=-1").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("INVALID_PAGINATION");
    }

    @Test
    void zeroSizeIsRejectedWith400() throws Exception {
        staffAdmin("zero-size@example.com");
        Cookie session = logIn("zero-size@example.com");

        var response = mockMvc.get().uri("/api/tenants?size=0").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("INVALID_PAGINATION");
    }

    @Test
    void searchMatchesByNameOnly() throws Exception {
        staffAdmin("search-name@example.com");
        String marker = "NameOnly" + System.nanoTime();
        tenantRepository.saveAndFlush(new Tenant(marker + "Corp"));
        Cookie session = logIn("search-name@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants?search=" + marker.toLowerCase())
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains(marker + "Corp");
    }

    @Test
    void searchMatchesByCnpjOnly() throws Exception {
        staffAdmin("search-cnpj@example.com");
        String marker = "C" + (System.nanoTime() % 1_000_000_000L);
        Tenant tenant = new Tenant("Ordinary " + System.nanoTime());
        tenant.setCnpj(marker);
        tenantRepository.saveAndFlush(tenant);
        Cookie session = logIn("search-cnpj@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants?search=" + marker.toLowerCase())
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains(marker);
    }

    @Test
    void searchMatchesByRazaoSocialOnly() throws Exception {
        staffAdmin("search-razao@example.com");
        String marker = "RazaoOnly" + System.nanoTime();
        Tenant tenant = new Tenant("Ordinary " + System.nanoTime());
        tenant.setRazaoSocial("Something " + marker);
        tenantRepository.saveAndFlush(tenant);
        Cookie session = logIn("search-razao@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants?search=" + marker.toLowerCase())
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains(marker);
    }

    @Test
    void filteredTotalElementsReflectOnlyMatchedRows() throws Exception {
        staffAdmin("filtered-total@example.com");
        String marker = "FilteredTotal" + System.nanoTime();
        tenantRepository.saveAndFlush(new Tenant(marker + "A"));
        tenantRepository.saveAndFlush(new Tenant(marker + "B"));
        tenantRepository.saveAndFlush(new Tenant("Unrelated " + System.nanoTime()));
        Cookie session = logIn("filtered-total@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants?search=" + marker.toLowerCase())
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat((Number) JsonPath.read(body, "$.totalElements")).isEqualTo(2);
    }

    @Test
    void pastEndPageReturnsEmptyContentWithCorrectTotals() throws Exception {
        staffAdmin("past-end@example.com");
        String marker = "PastEndCtrl" + System.nanoTime();
        tenantRepository.saveAndFlush(new Tenant(marker));
        Cookie session = logIn("past-end@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants?page=50&size=20&search=" + marker.toLowerCase())
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat((java.util.List<?>) JsonPath.read(body, "$.content")).isEmpty();
        assertThat((Number) JsonPath.read(body, "$.totalElements")).isEqualTo(1);
        assertThat((Number) JsonPath.read(body, "$.totalPages")).isEqualTo(1);
    }

    @Test
    void staffAdminSucceedsUnconditionally() throws Exception {
        staffAdmin("ctrl-staffadmin@example.com");
        Cookie session = logIn("ctrl-staffadmin@example.com");

        var response = mockMvc.get().uri("/api/tenants").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
    }

    @Test
    void ungrantedStaffIsRejected() throws Exception {
        limitedStaff("ctrl-ungranted@example.com");
        Cookie session = logIn("ctrl-ungranted@example.com");

        var response = mockMvc.get().uri("/api/tenants").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }
}
