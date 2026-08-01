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
    @Autowired private ActiveMemberSnapshotRepository activeMemberSnapshotRepository;
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

    private User staffAdmin(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(br.com.conectabyte.knowly.tenancy.GlobalRole.STAFF_ADMIN);
        return userRepository.saveAndFlush(user);
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
        assertThat(
                        mockMvc.get()
                                .uri("/api/tenants/metrics/messages/timeseries")
                                .cookie(session)
                                .exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(
                        mockMvc.get()
                                .uri("/api/tenants/metrics/articles/timeseries")
                                .cookie(session)
                                .exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mockMvc.get().uri("/api/tenants/metrics/members").cookie(session).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(
                        mockMvc.get()
                                .uri("/api/tenants/metrics/members/timeseries")
                                .cookie(session)
                                .exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mockMvc.get().uri("/api/tenants/metrics/export").cookie(session).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void membersTimeseriesReturnsSevenChronologicalDaysZeroFilledForTenantAOnly() throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        memberWithPermissions("memberstimeseries@example.com", tenantA, Permission.DASHBOARD_VIEW);
        java.time.LocalDate today = java.time.LocalDate.now();
        activeMemberSnapshotRepository.upsert(tenantA.getId(), today, 5L, "system:test");
        activeMemberSnapshotRepository.upsert(
                tenantA.getId(), today.minusDays(2), 3L, "system:test");
        activeMemberSnapshotRepository.upsert(tenantB.getId(), today, 999L, "system:test");
        Cookie session = logIn("memberstimeseries@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/members/timeseries?period=7d")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        long dayOccurrences =
                java.util.regex.Pattern.compile("\"date\":").matcher(body).results().count();
        assertThat(dayOccurrences).isEqualTo(7);
        long totalCount =
                java.util.regex.Pattern.compile("\"count\":(\\d+)")
                        .matcher(body)
                        .results()
                        .mapToLong(m -> Long.parseLong(m.group(1)))
                        .sum();
        assertThat(totalCount).isEqualTo(8L);
        assertThat(body).doesNotContain("999");
    }

    @Test
    void membersTimeseriesRejectsAnInvalidPeriod() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("membersbadperiod@example.com", tenant, Permission.DASHBOARD_VIEW);
        Cookie session = logIn("membersbadperiod@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/members/timeseries?period=bogus")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("INVALID_PERIOD");
    }

    @Test
    void membersMetricEndpointRemainsUnchangedByTheTimeseriesFeature() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("membersregression@example.com", tenant, Permission.DASHBOARD_VIEW);
        Cookie session = logIn("membersregression@example.com");

        var response = mockMvc.get().uri("/api/tenants/metrics/members").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"activeCount\":").contains("\"inactiveCount\":");
        assertThat(body).doesNotContain("\"days\":");
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
    void messagesTimeseriesReturnsSevenChronologicalDaysWithPerRoleCountsForTenantAOnly()
            throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        User caller =
                memberWithPermissions(
                        "msgtimeseries@example.com", tenantA, Permission.DASHBOARD_VIEW);
        Conversation conversationA =
                conversationRepository.saveAndFlush(new Conversation(tenantA, caller));
        Conversation conversationB =
                conversationRepository.saveAndFlush(new Conversation(tenantB, caller));
        Instant now = Instant.now();
        backdateMessage(
                messageRepository.saveAndFlush(new Message(conversationA, MessageRole.USER, "q1")),
                now);
        backdateMessage(
                messageRepository.saveAndFlush(
                        new Message(conversationA, MessageRole.ASSISTANT, "a1")),
                now);
        backdateMessage(
                messageRepository.saveAndFlush(new Message(conversationA, MessageRole.USER, "q2")),
                now.minus(2, ChronoUnit.DAYS));
        backdateMessage(
                messageRepository.saveAndFlush(
                        new Message(conversationB, MessageRole.USER, "other tenant")),
                now);
        Cookie session = logIn("msgtimeseries@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/messages/timeseries?period=7d")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        long dayOccurrences =
                java.util.regex.Pattern.compile("\"date\":").matcher(body).results().count();
        assertThat(dayOccurrences).isEqualTo(7);
        long userTotal =
                java.util.regex.Pattern.compile("\"userCount\":(\\d+)")
                        .matcher(body)
                        .results()
                        .mapToLong(m -> Long.parseLong(m.group(1)))
                        .sum();
        long assistantTotal =
                java.util.regex.Pattern.compile("\"assistantCount\":(\\d+)")
                        .matcher(body)
                        .results()
                        .mapToLong(m -> Long.parseLong(m.group(1)))
                        .sum();
        assertThat(userTotal).isEqualTo(2);
        assertThat(assistantTotal).isEqualTo(1);
        assertThat(body).doesNotContain("other tenant");
    }

    @Test
    void messagesTimeseriesRejectsAnInvalidPeriod() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("msgbadperiod@example.com", tenant, Permission.DASHBOARD_VIEW);
        Cookie session = logIn("msgbadperiod@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/messages/timeseries?period=bogus")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("INVALID_PERIOD");
    }

    @Test
    void articlesTimeseriesReturnsSevenChronologicalDaysExcludingInactiveArticlesAndOtherTenants()
            throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        memberWithPermissions("articletimeseries@example.com", tenantA, Permission.DASHBOARD_VIEW);
        Instant now = Instant.now();
        backdateArticle(anArticle(tenantA, "Active today"), now);
        Article inactive = anArticle(tenantA, "Inactive today");
        inactive.setActive(false);
        articleRepository.saveAndFlush(inactive);
        backdateArticle(inactive, now);
        backdateArticle(anArticle(tenantA, "Active 2 days ago"), now.minus(2, ChronoUnit.DAYS));
        backdateArticle(anArticle(tenantB, "Other tenant"), now);
        Cookie session = logIn("articletimeseries@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/articles/timeseries?period=7d")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        long dayOccurrences =
                java.util.regex.Pattern.compile("\"date\":").matcher(body).results().count();
        assertThat(dayOccurrences).isEqualTo(7);
        long totalCount =
                java.util.regex.Pattern.compile("\"count\":(\\d+)")
                        .matcher(body)
                        .results()
                        .mapToLong(m -> Long.parseLong(m.group(1)))
                        .sum();
        assertThat(totalCount).isEqualTo(2);
    }

    @Test
    void articlesTimeseriesRejectsAnInvalidPeriod() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("articlebadperiod@example.com", tenant, Permission.DASHBOARD_VIEW);
        Cookie session = logIn("articlebadperiod@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/articles/timeseries?period=bogus")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("INVALID_PERIOD");
    }

    @Test
    void membersMetricReportsActiveAndInactiveCountsForTheActiveTenantOnly() throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        memberWithPermissions("membersviewer@example.com", tenantA, Permission.DASHBOARD_VIEW);
        User inactiveUser = userRepository.saveAndFlush(new User("inactivemember@example.com"));
        TenantMembership inactiveMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(inactiveUser, tenantA, MembershipRole.MEMBER));
        inactiveMembership.setActive(false);
        tenantMembershipRepository.saveAndFlush(inactiveMembership);
        User otherTenantUser =
                userRepository.saveAndFlush(new User("otherTenantMember@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(otherTenantUser, tenantB, MembershipRole.MEMBER));
        Cookie session = logIn("membersviewer@example.com");

        var response = mockMvc.get().uri("/api/tenants/metrics/members").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"activeCount\":1").contains("\"inactiveCount\":1");
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

    @Test
    void conversationsMetricPeriodFilterCountsOnlyConversationsWithinTheWindow() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        User owner =
                memberWithPermissions("convperiod@example.com", tenant, Permission.DASHBOARD_VIEW);
        Instant now = Instant.now();
        backdateConversation(
                conversationRepository.saveAndFlush(new Conversation(tenant, owner)), now);
        backdateConversation(
                conversationRepository.saveAndFlush(new Conversation(tenant, owner)),
                now.minus(60, ChronoUnit.DAYS));
        Cookie session = logIn("convperiod@example.com");

        var filtered =
                mockMvc.get()
                        .uri("/api/tenants/metrics/conversations?period=30d")
                        .cookie(session)
                        .exchange();
        var unfiltered =
                mockMvc.get().uri("/api/tenants/metrics/conversations").cookie(session).exchange();

        assertThat(filtered).hasStatus(HttpStatus.OK);
        assertThat(filtered.getResponse().getContentAsString()).contains("\"startedCount\":1");
        assertThat(unfiltered).hasStatus(HttpStatus.OK);
        assertThat(unfiltered.getResponse().getContentAsString()).contains("\"startedCount\":2");
    }

    @Test
    void conversationsMetricRejectsAnInvalidPeriod() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("convbadperiod@example.com", tenant, Permission.DASHBOARD_VIEW);
        Cookie session = logIn("convbadperiod@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/conversations?period=bogus")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("INVALID_PERIOD");
    }

    @Test
    void messagesMetricPeriodFilterCountsOnlyMessagesWithinTheWindow() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        User owner =
                memberWithPermissions("msgperiod@example.com", tenant, Permission.DASHBOARD_VIEW);
        Conversation conversation =
                conversationRepository.saveAndFlush(new Conversation(tenant, owner));
        Instant now = Instant.now();
        backdateMessage(
                messageRepository.saveAndFlush(new Message(conversation, MessageRole.USER, "q1")),
                now);
        backdateMessage(
                messageRepository.saveAndFlush(new Message(conversation, MessageRole.USER, "q2")),
                now.minus(60, ChronoUnit.DAYS));
        Cookie session = logIn("msgperiod@example.com");

        var filtered =
                mockMvc.get()
                        .uri("/api/tenants/metrics/messages?period=30d")
                        .cookie(session)
                        .exchange();

        assertThat(filtered).hasStatus(HttpStatus.OK);
        assertThat(filtered.getResponse().getContentAsString()).contains("\"sentCount\":1");
    }

    @Test
    void messagesMetricRejectsAnInvalidPeriod() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("msgbadperiod2@example.com", tenant, Permission.DASHBOARD_VIEW);
        Cookie session = logIn("msgbadperiod2@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/messages?period=bogus")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("INVALID_PERIOD");
    }

    @Test
    void exportReturnsACsvFileWithAggregatesAndPerDayRowsExcludingRawContentAndOtherTenants()
            throws Exception {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        User owner =
                memberWithPermissions("exporter@example.com", tenantA, Permission.DASHBOARD_VIEW);
        anArticle(tenantA, "Exported Article Title");
        Article otherTenantArticle = anArticle(tenantB, "Secret Other Tenant Article");
        Conversation conversation =
                conversationRepository.saveAndFlush(new Conversation(tenantA, owner));
        messageRepository.saveAndFlush(
                new Message(conversation, MessageRole.USER, "super secret user content"));
        messageRepository.saveAndFlush(
                new Message(conversation, MessageRole.ASSISTANT, "super secret assistant reply"));
        User inactiveUser = userRepository.saveAndFlush(new User("inactiveexport@example.com"));
        TenantMembership inactiveMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(inactiveUser, tenantA, MembershipRole.MEMBER));
        inactiveMembership.setActive(false);
        tenantMembershipRepository.saveAndFlush(inactiveMembership);
        Cookie session = logIn("exporter@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/export?period=all")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentType()).startsWith("text/csv");
        assertThat(response.getResponse().getHeader("Content-Disposition")).contains("attachment");
        String body = response.getResponse().getContentAsString();
        assertThat(body)
                .contains("active_article_count,1")
                .contains("conversation_count,1")
                .contains("user_message_count,1")
                .contains("assistant_message_count,1")
                .contains("member_active_count,1")
                .contains("member_inactive_count,1")
                .contains("date,article_count")
                .contains("date,conversation_count")
                .contains("date,user_message_count,assistant_message_count");
        assertThat(body)
                .doesNotContain("super secret user content")
                .doesNotContain("super secret assistant reply")
                .doesNotContain("Exported Article Title")
                .doesNotContain("Secret Other Tenant Article");
        assertThat(otherTenantArticle.getTitle()).isEqualTo("Secret Other Tenant Article");
    }

    @Test
    void exportRejectsAnInvalidPeriod() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("exportbadperiod@example.com", tenant, Permission.DASHBOARD_VIEW);
        Cookie session = logIn("exportbadperiod@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/tenants/metrics/export?period=bogus")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("INVALID_PERIOD");
    }

    // --- No active tenant (e.g. staff who hasn't switched into one) ---

    @Test
    void staffWithNoActiveTenantGetsTenantSelectionRequiredNotAccessDenied() throws Exception {
        staffAdmin("no-active-tenant-staff@example.com");
        Cookie session = logIn("no-active-tenant-staff@example.com");

        var response =
                mockMvc.get().uri("/api/tenants/metrics/articles").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
        assertThat(response.getResponse().getContentAsString())
                .contains("TENANT_SELECTION_REQUIRED");
    }
}
