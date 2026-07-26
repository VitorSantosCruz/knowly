package br.com.conectabyte.knowly.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.conversation.Message;
import br.com.conectabyte.knowly.conversation.MessageArticleCitation;
import br.com.conectabyte.knowly.conversation.MessageArticleCitationRepository;
import br.com.conectabyte.knowly.conversation.MessageRepository;
import br.com.conectabyte.knowly.conversation.MessageRole;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetricsControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MessageArticleCitationRepository messageArticleCitationRepository;
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

    private User memberWithPermissions(String email, Tenant tenant, Permission... permissions) {
        User user = userRepository.saveAndFlush(new User(email));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));
        for (Permission permission : permissions) {
            directPermissionGrantRepository.saveAndFlush(
                    new DirectPermissionGrant(membership, permission));
        }
        return user;
    }

    private void backdateConversation(Conversation conversation, Instant createdAt) {
        jdbcTemplate.update(
                "update conversations set created_at = ? where id = ?",
                java.sql.Timestamp.from(createdAt),
                conversation.getId());
    }

    private void backdateArticle(Article article, Instant createdAt) {
        jdbcTemplate.update(
                "update articles set created_at = ? where id = ?",
                java.sql.Timestamp.from(createdAt),
                article.getId());
    }

    private void backdateMessage(Message message, Instant createdAt) {
        jdbcTemplate.update(
                "update messages set created_at = ? where id = ?",
                java.sql.Timestamp.from(createdAt),
                message.getId());
    }

    private Article anArticle(Tenant tenant, String title) {
        return articleRepository.saveAndFlush(
                new Article(tenant, title, "key", "file.pdf", "application/pdf"));
    }

    @Test
    void eachMetricsEndpointRequiresDashboardViewPermissionIndependently() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("NoPerm Tenant"));
        memberWithPermissions("noperm@example.com", tenant);
        Cookie session = logIn("noperm@example.com");

        assertThat(mockMvc.get().uri("/api/tenants/metrics/articles").cookie(session).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(
                        mockMvc.get()
                                .uri("/api/tenants/metrics/articles/usage")
                                .cookie(session)
                                .exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(
                        mockMvc.get()
                                .uri("/api/tenants/metrics/conversations")
                                .cookie(session)
                                .exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mockMvc.get().uri("/api/tenants/metrics/messages").cookie(session).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(
                        mockMvc.get()
                                .uri("/api/tenants/metrics/conversations/timeseries")
                                .cookie(session)
                                .exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void articleCountReflectsOnlyActiveArticlesInTheActiveTenant() throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        memberWithPermissions("viewer@example.com", tenantA, Permission.DASHBOARD_VIEW);
        anArticle(tenantA, "A1");
        Article deleted = anArticle(tenantA, "A2 deleted");
        deleted.setActive(false);
        articleRepository.saveAndFlush(deleted);
        anArticle(tenantB, "B1");
        Cookie session = logIn("viewer@example.com");

        var response =
                mockMvc.get().uri("/api/tenants/metrics/articles").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"totalCount\":1");
    }

    @Test
    void articleUsageRanksByCitationCountAndExcludesOtherTenants() throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        User owner =
                memberWithPermissions("viewer2@example.com", tenantA, Permission.DASHBOARD_VIEW);
        Article popular = anArticle(tenantA, "Popular");
        Article lessPopular = anArticle(tenantA, "Less popular");
        Article otherTenantArticle = anArticle(tenantB, "Other tenant");
        Conversation conversation =
                conversationRepository.saveAndFlush(new Conversation(tenantA, owner));
        Message message1 =
                messageRepository.saveAndFlush(
                        new Message(conversation, MessageRole.ASSISTANT, "answer 1"));
        Message message2 =
                messageRepository.saveAndFlush(
                        new Message(conversation, MessageRole.ASSISTANT, "answer 2"));
        messageArticleCitationRepository.saveAndFlush(
                new MessageArticleCitation(message1, popular));
        messageArticleCitationRepository.saveAndFlush(
                new MessageArticleCitation(message2, popular));
        messageArticleCitationRepository.saveAndFlush(
                new MessageArticleCitation(message1, lessPopular));
        Conversation otherTenantConversation =
                conversationRepository.saveAndFlush(new Conversation(tenantB, owner));
        Message otherTenantMessage =
                messageRepository.saveAndFlush(
                        new Message(otherTenantConversation, MessageRole.ASSISTANT, "other"));
        messageArticleCitationRepository.saveAndFlush(
                new MessageArticleCitation(otherTenantMessage, otherTenantArticle));
        Cookie session = logIn("viewer2@example.com");

        var response =
                mockMvc.get().uri("/api/tenants/metrics/articles/usage").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("Popular").contains("Less popular");
        assertThat(body).doesNotContain("Other tenant");
        assertThat(body.indexOf("Popular")).isLessThan(body.indexOf("Less popular"));
    }

    @Test
    void conversationsMetricIsTenantWideNotJustTheCallersOwn() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("Other Tenant"));
        User caller =
                memberWithPermissions("caller@example.com", tenant, Permission.DASHBOARD_VIEW);
        User otherUser = userRepository.saveAndFlush(new User("otheruser@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(otherUser, tenant, MembershipRole.MEMBER));
        conversationRepository.saveAndFlush(new Conversation(tenant, caller));
        conversationRepository.saveAndFlush(new Conversation(tenant, otherUser));
        conversationRepository.saveAndFlush(new Conversation(otherTenant, caller));
        Cookie session = logIn("caller@example.com");

        var response =
                mockMvc.get().uri("/api/tenants/metrics/conversations").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"startedCount\":2");
    }

    @Test
    void conversationsTimeseriesReturnsSevenChronologicalDaysWithZeroCountDaysForTenantAOnly()
            throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        User caller =
                memberWithPermissions("timeseries@example.com", tenantA, Permission.DASHBOARD_VIEW);
        User otherUser = userRepository.saveAndFlush(new User("othertimeseries@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(otherUser, tenantB, MembershipRole.MEMBER));
        Instant now = Instant.now();
        // Tenant A: conversations on today and 2 days ago only (5 of the 7 days have none).
        backdateConversation(
                conversationRepository.saveAndFlush(new Conversation(tenantA, caller)), now);
        backdateConversation(
                conversationRepository.saveAndFlush(new Conversation(tenantA, caller)),
                now.minus(2, ChronoUnit.DAYS));
        // Tenant B conversation must never leak into tenant A's response.
        backdateConversation(
                conversationRepository.saveAndFlush(new Conversation(tenantB, otherUser)), now);
        Cookie session = logIn("timeseries@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/conversations/timeseries?period=7d")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"days\":[");
        long dateOccurrences =
                java.util.regex.Pattern.compile("\"date\":").matcher(body).results().count();
        assertThat(dateOccurrences).isEqualTo(7);
        long totalCount =
                java.util.regex.Pattern.compile("\"count\":(\\d+)")
                        .matcher(body)
                        .results()
                        .mapToLong(m -> Long.parseLong(m.group(1)))
                        .sum();
        assertThat(totalCount).isEqualTo(2);
    }

    @Test
    void conversationsTimeseriesRejectsAnInvalidPeriod() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("badperiod@example.com", tenant, Permission.DASHBOARD_VIEW);
        Cookie session = logIn("badperiod@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/conversations/timeseries?period=bogus")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("INVALID_PERIOD");
    }

    @Test
    void messagesMetricReportsSeparateSentAndReceivedCountsTenantWide() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        User owner =
                memberWithPermissions("msgviewer@example.com", tenant, Permission.DASHBOARD_VIEW);
        Conversation conversation =
                conversationRepository.saveAndFlush(new Conversation(tenant, owner));
        messageRepository.saveAndFlush(new Message(conversation, MessageRole.USER, "q1"));
        messageRepository.saveAndFlush(new Message(conversation, MessageRole.USER, "q2"));
        messageRepository.saveAndFlush(new Message(conversation, MessageRole.ASSISTANT, "a1"));
        Cookie session = logIn("msgviewer@example.com");

        var response =
                mockMvc.get().uri("/api/tenants/metrics/messages").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"sentCount\":2").contains("\"receivedCount\":1");
    }
}
