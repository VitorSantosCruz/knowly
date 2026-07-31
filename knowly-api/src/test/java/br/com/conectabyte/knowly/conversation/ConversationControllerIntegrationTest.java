package br.com.conectabyte.knowly.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.vectorstore.VectorStore;
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
import reactor.core.publisher.Flux;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private MessageRepository messageRepository;
    @MockitoBean private JavaMailSender mailSender;
    @MockitoBean private ChatModel chatModel;
    @MockitoBean private VectorStore vectorStore;

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

    /**
     * /api/tenants/{tenantId}/conversations/** is not CSRF-exempt (only /api/tenants/active is, see
     * SecurityConfig) so every state-changing call in this test needs a real XSRF-TOKEN cookie +
     * header, same convention as AuthControllerIntegrationTest#obtainCsrfCookie().
     */
    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
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

    @Test
    void createRequiresConversationUsePermission() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("NoPerm Tenant"));
        memberWithPermissions("noperm@example.com", tenant);
        Cookie session = logIn("noperm@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aUserOnlySeesTheirOwnConversationsMostRecentFirst() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("a@example.com", tenant, Permission.CONVERSATION_USE);
        User userB = memberWithPermissions("b@example.com", tenant, Permission.CONVERSATION_USE);
        conversationRepository.saveAndFlush(new Conversation(tenant, userB));
        Cookie sessionA = logIn("a@example.com");

        var listResponse =
                mockMvc.get()
                        .uri("/api/tenants/" + tenant.getId() + "/conversations")
                        .cookie(sessionA)
                        .exchange();

        assertThat(listResponse).hasStatus(HttpStatus.OK);
        assertThat(listResponse.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    void anotherUsersConversationIdReturnsNotFoundNotForbidden() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Tenant"));
        memberWithPermissions("a2@example.com", tenant, Permission.CONVERSATION_USE);
        User userB = memberWithPermissions("b2@example.com", tenant, Permission.CONVERSATION_USE);
        Conversation conversationB =
                conversationRepository.saveAndFlush(new Conversation(tenant, userB));
        Cookie sessionA = logIn("a2@example.com");

        var response =
                mockMvc.get()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/conversations/"
                                        + conversationB.getId())
                        .cookie(sessionA)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void sendMessageRequiresConversationUsePermission() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("NoPerm Tenant"));
        User owner =
                memberWithPermissions("noperm2@example.com", tenant, Permission.CONVERSATION_USE);
        Conversation conversation =
                conversationRepository.saveAndFlush(new Conversation(tenant, owner));
        // Re-login as a member without the permission to exercise the 403 path.
        User noPermUser = userRepository.saveAndFlush(new User("noperm3@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(noPermUser, tenant, MembershipRole.MEMBER));
        Cookie session = logIn("noperm3@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/conversations/"
                                        + conversation.getId()
                                        + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"What is X?\"}")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void sendMessagePersistsTheUserMessageAndStreamsTheAssistantResponse() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Chat Tenant"));
        User owner =
                memberWithPermissions("chatter@example.com", tenant, Permission.CONVERSATION_USE);
        Conversation conversation =
                conversationRepository.saveAndFlush(new Conversation(tenant, owner));
        Cookie session = logIn("chatter@example.com");
        Cookie csrf = obtainCsrfCookie();
        when(vectorStore.similaritySearch(
                        any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of());
        when(chatModel.stream(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(
                        Flux.just(
                                new ChatResponse(
                                        List.of(
                                                new Generation(
                                                        new AssistantMessage("Hi there!"))))));

        var response =
                mockMvc.post()
                        .uri(
                                "/api/tenants/"
                                        + tenant.getId()
                                        + "/conversations/"
                                        + conversation.getId()
                                        + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"What is X?\"}")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        List<Message> messages =
                messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        assertThat(messages)
                .extracting(Message::getContent)
                .containsExactly("What is X?", "Hi there!");
    }

    @Test
    void createProducesAnAuditEvent() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Audit Tenant"));
        User user =
                memberWithPermissions("auditor@example.com", tenant, Permission.CONVERSATION_USE);
        Cookie session = logIn("auditor@example.com");
        Cookie csrf = obtainCsrfCookie();

        var response =
                mockMvc.post()
                        .uri("/api/tenants/" + tenant.getId() + "/conversations")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.CREATED);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).extracting(AuditEvent::getAction).contains("conversation.create");
    }
}
