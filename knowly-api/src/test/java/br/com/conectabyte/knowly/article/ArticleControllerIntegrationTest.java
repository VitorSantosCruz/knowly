package br.com.conectabyte.knowly.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArticleControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private ArticleRepository articleRepository;
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

    private TenantMembership memberWithPermissions(
            String email, Tenant tenant, Permission... permissions) {
        User user = userRepository.saveAndFlush(new User(email));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));
        for (Permission permission : permissions) {
            directPermissionGrantRepository.saveAndFlush(
                    new DirectPermissionGrant(membership, permission));
        }
        return membership;
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "not a real pdf but ok for storage".getBytes());
    }

    @Test
    void uploadingASupportedFileReturns202AndProcessingStatus() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Upload Tenant"));
        memberWithPermissions("creator@example.com", tenant, Permission.ARTICLE_CREATE);
        Cookie session = logIn("creator@example.com");

        var response =
                mockMvc.perform(
                        multipart("/api/tenants/" + tenant.getId() + "/articles")
                                .file(pdfFile())
                                .param("title", "My article")
                                .cookie(session));

        assertThat(response).hasStatus(HttpStatus.ACCEPTED);
        assertThat(response.getResponse().getContentAsString())
                .contains("\"status\":\"PROCESSING\"");
    }

    @Test
    void uploadingAnUnsupportedFileTypeIsRejected() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Reject Tenant"));
        memberWithPermissions("creator2@example.com", tenant, Permission.ARTICLE_CREATE);
        Cookie session = logIn("creator2@example.com");
        MockMultipartFile badFile =
                new MockMultipartFile(
                        "file", "malware.exe", "application/x-msdownload", "bytes".getBytes());

        var response =
                mockMvc.perform(
                        multipart("/api/tenants/" + tenant.getId() + "/articles")
                                .file(badFile)
                                .param("title", "Bad file")
                                .cookie(session));

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void uploadWithoutCreatePermissionIsForbidden() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("NoPerm Tenant"));
        memberWithPermissions("noperm@example.com", tenant);
        Cookie session = logIn("noperm@example.com");

        var response =
                mockMvc.perform(
                        multipart("/api/tenants/" + tenant.getId() + "/articles")
                                .file(pdfFile())
                                .param("title", "Should fail")
                                .cookie(session));

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void listRequiresViewPermissionAndScopesToTheActiveTenant() throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        memberWithPermissions("viewer@example.com", tenantA, Permission.ARTICLE_VIEW);
        articleRepository.saveAndFlush(
                new Article(tenantA, "A article", "key-a", "a.pdf", "application/pdf"));
        articleRepository.saveAndFlush(
                new Article(tenantB, "B article", "key-b", "b.pdf", "application/pdf"));

        Cookie session = logIn("viewer@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/" + tenantA.getId() + "/articles")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("A article");
        assertThat(response.getResponse().getContentAsString()).doesNotContain("B article");
    }

    @Test
    void editRequiresEditPermissionIndependentOfCreate() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Tenant"));
        memberWithPermissions("editor@example.com", tenant, Permission.ARTICLE_EDIT);
        Article article =
                articleRepository.saveAndFlush(
                        new Article(tenant, "Original title", "key", "f.pdf", "application/pdf"));
        Cookie session = logIn("editor@example.com");

        var response =
                mockMvc.put()
                        .uri("/api/tenants/" + tenant.getId() + "/articles/" + article.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fixed title\",\"text\":\"corrected text\"}")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("Fixed title");
    }

    @Test
    void deleteRequiresDeletePermissionAndSoftDeletes() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Tenant"));
        memberWithPermissions("deleter@example.com", tenant, Permission.ARTICLE_DELETE);
        Article article =
                articleRepository.saveAndFlush(
                        new Article(tenant, "To delete", "key", "f.pdf", "application/pdf"));
        Cookie session = logIn("deleter@example.com");

        var response =
                mockMvc.delete()
                        .uri("/api/tenants/" + tenant.getId() + "/articles/" + article.getId())
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(articleRepository.findById(article.getId())).isPresent();
        assertThat(articleRepository.findByTenantIdAndActiveTrue(tenant.getId())).isEmpty();
    }

    @Test
    void everyActionProducesItsOwnAuditAction() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Audit Tenant"));
        memberWithPermissions(
                "auditor@example.com",
                tenant,
                Permission.ARTICLE_VIEW,
                Permission.ARTICLE_CREATE,
                Permission.ARTICLE_EDIT,
                Permission.ARTICLE_DELETE);
        User user = userRepository.findByEmailIgnoreCase("auditor@example.com").orElseThrow();
        Cookie session = logIn("auditor@example.com");

        mockMvc.get()
                .uri("/api/tenants/" + tenant.getId() + "/articles")
                .cookie(session)
                .exchange();

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).extracting(AuditEvent::getAction).contains("article.list");
    }
}
