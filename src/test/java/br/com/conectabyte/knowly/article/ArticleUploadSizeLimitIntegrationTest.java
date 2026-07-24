package br.com.conectabyte.knowly.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Isolated from ArticleControllerIntegrationTest: overrides the max file size very low. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "knowly.article.max-file-size=5B")
class ArticleUploadSizeLimitIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private LoginCodeService loginCodeService;
    @MockitoBean private JavaMailSender mailSender;

    @Test
    void uploadingAFileExceedingTheConfiguredMaxSizeIsRejected() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Size Tenant"));
        User user = userRepository.saveAndFlush(new User("sizecheck@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_CREATE));

        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        String code = loginCodeService.generate("sizecheck@example.com");
        var loginResult =
                mockMvc.post()
                        .uri("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sizecheck@example.com\",\"code\":\"" + code + "\"}")
                        .exchange();
        Cookie session = loginResult.getResponse().getCookie("SESSION");

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "sample.pdf",
                        "application/pdf",
                        "this is more than five bytes".getBytes());

        var response =
                mockMvc.perform(
                        multipart("/api/tenants/" + tenant.getId() + "/articles")
                                .file(file)
                                .param("title", "Too big")
                                .cookie(session));

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("FILE_TOO_LARGE");
    }
}
