package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * specify/features/tenant-crud/SPEC.md REQ-8 through REQ-18, end to end against the deletion
 * confirmation token + {@code DELETE /api/tenants/{tenantId}} flow.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantDeleteIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
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

    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    private String generateToken(Cookie session, Cookie csrf, Long tenantId) {
        var response =
                mockMvc.post()
                        .uri("/api/tenants/{id}/deletion-confirmation-token", tenantId)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        try {
            return response.getResponse()
                    .getContentAsString()
                    .replaceAll(".*\"word\":\"(.*)\".*", "$1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void fullDeleteFlowSoftDeletesTenantAndDeactivatesMembershipsButLeavesOtherDataUntouched() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Flow Co"));
        User member = userRepository.saveAndFlush(new User("delete-flow-member@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));
        Article article =
                articleRepository.saveAndFlush(
                        new Article(
                                tenant,
                                "Pre-deletion Article",
                                "tenants/x/articles/1/original",
                                "a.pdf",
                                "application/pdf"));
        AccessGroup accessGroup =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));

        staffAdmin("delete-flow-staffadmin@example.com");
        Cookie session = logIn("delete-flow-staffadmin@example.com");
        Cookie csrf = obtainCsrfCookie();
        String word = generateToken(session, csrf, tenant.getId());

        var response =
                mockMvc.method(org.springframework.http.HttpMethod.DELETE)
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);

        Tenant persistedTenant = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persistedTenant.getDeletedAt()).isNotNull();

        assertThat(tenantMembershipRepository.findById(membership.getId()))
                .hasValueSatisfying(m -> assertThat(m.isActive()).isFalse());

        // REQ-10: Article/AccessGroup rows are completely untouched, verified via raw SQL to
        // bypass the tenant filter (the tenant is now soft-deleted and unreachable per REQ-11, so
        // a filtered repository read would legitimately return nothing -- this checks the *rows
        // themselves* are unmodified, not whether they're still reachable).
        Integer articleCount =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM articles WHERE id = ? AND title = ?",
                        Integer.class,
                        article.getId(),
                        "Pre-deletion Article");
        assertThat(articleCount).isEqualTo(1);

        Integer accessGroupCount =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM access_groups WHERE id = ? AND name = ?",
                        Integer.class,
                        accessGroup.getId(),
                        "Editors");
        assertThat(accessGroupCount).isEqualTo(1);
    }

    @Test
    void missingOrWrongWordIsRejectedWith400AndTenantIsUntouched() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Wrong Word Co"));
        staffAdmin("delete-wrong-word@example.com");
        Cookie session = logIn("delete-wrong-word@example.com");
        Cookie csrf = obtainCsrfCookie();
        generateToken(session, csrf, tenant.getId());

        var response =
                mockMvc.method(org.springframework.http.HttpMethod.DELETE)
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"totally-wrong-word\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void staffWithoutTenantDeleteOrTenantViewIsDenied403AndNoTokenIsGeneratedOrConsumed() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Ungranted Co"));
        User staff = userRepository.saveAndFlush(new User("delete-ungranted@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staff);
        Cookie session = logIn("delete-ungranted@example.com");
        Cookie csrf = obtainCsrfCookie();

        var tokenResponse =
                mockMvc.post()
                        .uri("/api/tenants/{id}/deletion-confirmation-token", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(tokenResponse).hasStatus(HttpStatus.FORBIDDEN);

        var deleteResponse =
                mockMvc.method(org.springframework.http.HttpMethod.DELETE)
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"irrelevant-word\"}")
                        .exchange();

        assertThat(deleteResponse).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void deletingAnAlreadyDeletedOrNonExistentTenantReturns404() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Already Deleted Flow Co"));
        tenant.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);
        staffAdmin("delete-already-flow@example.com");
        Cookie session = logIn("delete-already-flow@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.method(org.springframework.http.HttpMethod.DELETE)
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"irrelevant-word\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);

        var missingResponse =
                mockMvc.method(org.springframework.http.HttpMethod.DELETE)
                        .uri("/api/tenants/{id}", Long.MAX_VALUE)
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"irrelevant-word\"}")
                        .exchange();

        assertThat(missingResponse).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void noVolumeBasedRejectionRegardlessOfMembershipCount() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Volume Flow Co"));
        for (int i = 0; i < 30; i++) {
            User member =
                    userRepository.saveAndFlush(
                            new User("delete-volume-flow-" + i + "@example.com"));
            tenantMembershipRepository.saveAndFlush(
                    new TenantMembership(member, tenant, MembershipRole.MEMBER));
        }
        staffAdmin("delete-volume-flow-staffadmin@example.com");
        Cookie session = logIn("delete-volume-flow-staffadmin@example.com");
        Cookie csrf = obtainCsrfCookie();
        String word = generateToken(session, csrf, tenant.getId());

        var response =
                mockMvc.method(org.springframework.http.HttpMethod.DELETE)
                        .uri("/api/tenants/{id}", tenant.getId())
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + word + "\"}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getDeletedAt())
                .isNotNull();
    }

    // REQ-11: after soft-deletion, a member's switchActiveTenant and staff's act-as-tenant flow
    // against that tenant id both -> 403, audited as DENIED (end to end through the
    // controller/aspect -- confirms task 4's unit coverage holds through the full stack).

    @Test
    void memberCannotSwitchIntoASoftDeletedTenantAndTheAttemptIsAuditedAsDenied() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Switch Member Co"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("Delete Switch Other Co"));
        User member = userRepository.saveAndFlush(new User("delete-switch-member@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(member, tenant, MembershipRole.MEMBER));
        // A second active membership keeps login's auto-select-single-tenant path from firing
        // (which would itself hit the soft-deleted-tenant rejection during login, before this test
        // gets to exercise the explicit switch attempt) -- login resolves to "selection pending"
        // instead, then the test explicitly targets the soft-deleted tenant.
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(member, otherTenant, MembershipRole.MEMBER));
        tenant.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);

        Cookie session = logIn("delete-switch-member@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenant.getId() + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);

        var events = auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(member.getId());
        assertThat(events)
                .anySatisfy(
                        e -> {
                            assertThat(e.getAction()).isEqualTo("tenant.active_tenant.switch");
                            assertThat(e.getOutcome())
                                    .isEqualTo(br.com.conectabyte.knowly.audit.AuditOutcome.DENIED);
                        });
    }

    @Test
    void staffCannotActAsASoftDeletedTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Switch Staff Co"));
        tenant.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);
        staffAdmin("delete-switch-staffadmin@example.com");
        Cookie session = logIn("delete-switch-staffadmin@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenant.getId() + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    // REQ-12: a new tenant creation reusing a soft-deleted tenant's taxId succeeds, confirmed from
    // the API layer (not just the migration's raw-SQL constraint test).

    @Test
    void creatingANewTenantWithASoftDeletedTenantsTaxIdSucceeds() {
        String taxId = "EIN-REQ12-REUSE-" + System.nanoTime();
        Tenant deleted = new Tenant("Old Closed Co");
        deleted.setTaxId(taxId);
        deleted.setCountry("US");
        deleted.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(deleted);

        staffAdmin("delete-req12@example.com");
        Cookie session = logIn("delete-req12@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"name\":\"New Reonboarded Co\",\"legalName\":\"New Reonboarded"
                                        + " Ltda\",\"taxId\":\""
                                        + taxId
                                        + "\",\"country\":\"US\",\"contactEmail\":\"contact@reonboarded.com\","
                                        + "\"contactPhone\":\"11999999999\","
                                        + "\"address\":{\"postalCode\":\"01000-000\",\"street\":\"Rua"
                                        + " Um\",\"number\":\"1\",\"neighborhood\":\"Centro\","
                                        + "\"city\":\"Sao Paulo\",\"state\":\"SP\"},"
                                        + "\"adminEmail\":\"admin-reonboarded@example.com\","
                                        + "\"profile\":{\"fullName\":\"Test User\",\"birthDate\":\"1990-01-01\","
                                        + "\"cpf\":\"12345678901\",\"rg\":\"123456\",\"rgOrgaoEmissor\":\"SSP\","
                                        + "\"address\":{\"cep\":\"01000-000\",\"logradouro\":\"Rua"
                                        + " Um\",\"bairro\":\"Centro\",\"cidade\":\"Sao"
                                        + " Paulo\",\"estado\":\"SP\",\"pais\":\"Brasil\"},"
                                        + "\"contacts\":[{\"type\":\"OTHER\",\"value\":\"v\",\"isPrimary\":false}]}"
                                        + "}")
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(userRepository.findByEmailIgnoreCase("admin-reonboarded@example.com"))
                .isPresent();
    }

    @Test
    void everyDeletionAttemptIsAuditLogged() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Audit Flow Co"));
        User staff = staffAdmin("delete-audit-flow@example.com");
        Cookie session = logIn("delete-audit-flow@example.com");
        Cookie csrf = obtainCsrfCookie();
        String word = generateToken(session, csrf, tenant.getId());

        mockMvc.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/api/tenants/{id}", tenant.getId())
                .cookie(session)
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"word\":\"" + word + "\"}")
                .exchange();

        var events = auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(staff.getId());
        assertThat(events).anySatisfy(e -> assertThat(e.getAction()).isEqualTo("tenant.delete"));
    }
}
