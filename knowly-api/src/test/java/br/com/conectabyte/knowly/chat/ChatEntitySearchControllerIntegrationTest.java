package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.identity.UserProfile;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrantRepository;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * chat-message-search TASKS.md items 107-133 (unified entity search, 2026-08-10 amendment):
 * controller/HTTP-layer coverage for {@code GET /api/chat/search}, including both AppSec-required
 * cross-tenant regression tests (Gap 1: participant-groups union; Gap 2: JPQL
 * staff-no-active-tenant exposure for groups and RAG).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatEntitySearchControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ChatConversationRepository chatConversationRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ConversationRepository conversationRepository;
    @MockitoBean private JavaMailSender mailSender;

    @BeforeEach
    void resetLoginVelocityCounters() {
        Set<String> keys = redisTemplate.keys("auth:login-velocity:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

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

    private void switchActiveTenant(Cookie session, Long tenantId) {
        var response =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + "}")
                        .exchange();
        assertThat(response).hasStatus(HttpStatus.OK);
    }

    private User member(String email, Tenant tenant) {
        User user = userRepository.saveAndFlush(new User(email));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));
        return user;
    }

    private User staff(String email, GlobalRole role) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(role);
        return userRepository.saveAndFlush(user);
    }

    private void named(User user, String fullName) {
        UserProfile profile = new UserProfile(user);
        profile.setFullName(fullName);
        userProfileRepository.saveAndFlush(profile);
    }

    private ChatConversation group(Tenant tenant, String title, ChatGroupVisibility visibility) {
        ChatConversation c =
                new ChatConversation(ChatConversationKind.PEER_GROUP, tenant, title, null);
        c.setVisibility(visibility);
        return chatConversationRepository.saveAndFlush(c);
    }

    private void participate(ChatConversation conversation, User user) {
        chatParticipantRepository.saveAndFlush(new ChatParticipant(conversation, user));
    }

    private Conversation ragConversation(Tenant tenant, User owner, String title) {
        return conversationRepository.saveAndFlush(new Conversation(tenant, owner, title));
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult search(
            Cookie session, String query) {
        return mockMvc.get().uri("/api/chat/search").param("q", query).cookie(session).exchange();
    }

    // TASKS.md item 107
    @Test
    void happyPathReturnsAResponseDtoShapedBodyAndBlankQReturnsAResultDtoShapedBody()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Entity Search Happy Path Co"));
        member("entity-search-happy@example.com", tenant);
        Cookie session = logIn("entity-search-happy@example.com");
        switchActiveTenant(session, tenant.getId());

        var response = search(session, "anything");
        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"people\"", "\"groups\"", "\"support\"", "\"rag\"");

        var blank = mockMvc.get().uri("/api/chat/search").cookie(session).exchange();
        assertThat(blank).hasStatus(HttpStatus.OK);
        assertThat(blank.getResponse().getContentAsString()).contains("\"recentPlaces\"");
    }

    // TASKS.md item 109
    @Test
    void typeWithoutOffsetOrOutOfEnumTypeReturns400WithInvalidExpandParamCode() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Entity Search Expand Co"));
        member("entity-search-expand@example.com", tenant);
        Cookie session = logIn("entity-search-expand@example.com");

        var missingOffset =
                mockMvc.get()
                        .uri("/api/chat/search")
                        .param("q", "x")
                        .param("type", "people")
                        .cookie(session)
                        .exchange();
        assertThat(missingOffset).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(missingOffset.getResponse().getContentAsString())
                .contains("CHAT_SEARCH_INVALID_EXPAND_PARAM");

        var badType =
                mockMvc.get()
                        .uri("/api/chat/search")
                        .param("q", "x")
                        .param("type", "bogus")
                        .param("offset", "0")
                        .cookie(session)
                        .exchange();
        assertThat(badType).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(badType.getResponse().getContentAsString())
                .contains("CHAT_SEARCH_INVALID_EXPAND_PARAM");
    }

    // TASKS.md item 111
    @Test
    void validTypeAndOffsetReturnsOnlyThatSectionsResults() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Entity Search Expand Valid Co"));
        member("entity-search-expand-valid@example.com", tenant);
        group(tenant, "Expand Group", ChatGroupVisibility.PUBLIC);
        Cookie session = logIn("entity-search-expand-valid@example.com");
        switchActiveTenant(session, tenant.getId());

        var response =
                mockMvc.get()
                        .uri("/api/chat/search")
                        .param("q", "expand")
                        .param("type", "groups")
                        .param("offset", "0")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"results\"", "\"hasMore\"");
        assertThat(body).doesNotContain("\"people\"").doesNotContain("\"support\"");
    }

    // TASKS.md item 113 (REQ-19, groups)
    @Test
    void groupQueryMatchesParticipatingPublicAndRequestToJoinGroupsButNotPrivateOrCrossTenant()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ19 Co"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("REQ19 Other Co"));
        User caller = member("req19-caller@example.com", tenant);

        ChatConversation participating =
                group(tenant, "Zilchgroove Participating", ChatGroupVisibility.PRIVATE);
        participate(participating, caller);
        ChatConversation publicGroup =
                group(tenant, "Zilchgroove Public", ChatGroupVisibility.PUBLIC);
        ChatConversation requestToJoin =
                group(tenant, "Zilchgroove RTJ", ChatGroupVisibility.REQUEST_TO_JOIN);
        ChatConversation privateNotJoined =
                group(tenant, "Zilchgroove Private", ChatGroupVisibility.PRIVATE);
        ChatConversation crossTenant =
                group(otherTenant, "Zilchgroove Cross Tenant", ChatGroupVisibility.PUBLIC);

        Cookie session = logIn("req19-caller@example.com");
        switchActiveTenant(session, tenant.getId());

        var response = search(session, "zilchgroove");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body)
                .contains("Zilchgroove Participating", "Zilchgroove Public", "Zilchgroove RTJ");
        assertThat(body).doesNotContain("Zilchgroove Private");
        assertThat(body).doesNotContain("Zilchgroove Cross Tenant");
    }

    // TASKS.md item 115: AppSec-required regression (Gap 1, full stack)
    @Test
    void participantGroupsUnionNeverLeaksASameTitledGroupFromAnInactiveTenant() throws Exception {
        Tenant tenantOne = tenantRepository.saveAndFlush(new Tenant("Gap1 Tenant One"));
        Tenant tenantTwo = tenantRepository.saveAndFlush(new Tenant("Gap1 Tenant Two"));
        User caller = userRepository.saveAndFlush(new User("gap1-caller@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(caller, tenantOne, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(caller, tenantTwo, MembershipRole.MEMBER));

        ChatConversation groupOne =
                group(tenantOne, "Fandanglewisp Group", ChatGroupVisibility.PRIVATE);
        participate(groupOne, caller);
        ChatConversation groupTwo =
                group(tenantTwo, "Fandanglewisp Group", ChatGroupVisibility.PRIVATE);
        participate(groupTwo, caller);

        Cookie session = logIn("gap1-caller@example.com");
        switchActiveTenant(session, tenantOne.getId());

        var response = search(session, "fandanglewisp");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"id\":" + groupOne.getId());
        assertThat(body).doesNotContain("\"id\":" + groupTwo.getId());
    }

    // TASKS.md item 117: AppSec-required regression (Gap 2, full stack, groups)
    @Test
    void staffWithNoActiveTenantGetsZeroGroupResultsAcrossTenants() throws Exception {
        Tenant tenantOne = tenantRepository.saveAndFlush(new Tenant("Gap2 Groups Tenant One"));
        Tenant tenantTwo = tenantRepository.saveAndFlush(new Tenant("Gap2 Groups Tenant Two"));
        User staffCaller = staff("gap2-groups-staff@example.com", GlobalRole.STAFF);
        group(tenantOne, "Quirkleplumage Group", ChatGroupVisibility.PUBLIC);
        group(tenantTwo, "Quirkleplumage Group", ChatGroupVisibility.PUBLIC);

        Cookie session = logIn("gap2-groups-staff@example.com");

        var response = search(session, "quirkleplumage");

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString())
                .contains("\"groups\":{\"results\":[]");
    }

    // TASKS.md item 118: AppSec-required regression (Gap 2, full stack, RAG)
    @Test
    void staffWithNoActiveTenantGetsZeroRagResultsAcrossTenants() throws Exception {
        Tenant tenantOne = tenantRepository.saveAndFlush(new Tenant("Gap2 Rag Tenant One"));
        Tenant tenantTwo = tenantRepository.saveAndFlush(new Tenant("Gap2 Rag Tenant Two"));
        User staffCaller = staff("gap2-rag-staff@example.com", GlobalRole.STAFF);
        ragConversation(tenantOne, staffCaller, "Wobblenectar Articles");
        ragConversation(tenantTwo, staffCaller, "Wobblenectar Articles");

        Cookie session = logIn("gap2-rag-staff@example.com");

        var response = search(session, "wobblenectar");

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"rag\":{\"results\":[]");
    }

    // TASKS.md item 120 (REQ-20, people)
    @Test
    void nameMatchingUserSharingNoTenantOrStaffAnchorIsAbsentFromResults() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ20 Co"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("REQ20 Other Co"));
        User caller = member("req20-caller@example.com", tenant);
        User unrelated = member("req20-unrelated@example.com", otherTenant);
        named(unrelated, "Blorptastic Stranger");

        Cookie session = logIn("req20-caller@example.com");
        switchActiveTenant(session, tenant.getId());

        var response = search(session, "blorptastic");

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString())
                .doesNotContain("Blorptastic Stranger");
    }

    // TASKS.md item 122 (REQ-21, Support)
    @Test
    void supportQueryMatchesInBothLocalesAndOnlyForCallersWithAReachableChannel() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ21 Co"));
        User member = member("req21-member@example.com", tenant);
        ChatConversation channel =
                new ChatConversation(ChatConversationKind.SUPPORT, tenant, null, member);
        chatConversationRepository.saveAndFlush(channel);
        participate(channel, member);

        Cookie memberSession = logIn("req21-member@example.com");
        switchActiveTenant(memberSession, tenant.getId());

        var enResponse = search(memberSession, "support");
        assertThat(enResponse).hasStatus(HttpStatus.OK);
        assertThat(enResponse.getResponse().getContentAsString())
                .contains("\"channelId\":" + channel.getId());

        var ptResponse =
                mockMvc.get()
                        .uri("/api/chat/search")
                        .param("q", "suporte")
                        .header("Accept-Language", "pt-BR")
                        .cookie(memberSession)
                        .exchange();
        assertThat(ptResponse).hasStatus(HttpStatus.OK);
        assertThat(ptResponse.getResponse().getContentAsString())
                .contains("\"channelId\":" + channel.getId());

        User noChannel = member("req21-nochannel@example.com", tenant);
        Cookie noChannelSession = logIn("req21-nochannel@example.com");
        switchActiveTenant(noChannelSession, tenant.getId());
        var noChannelResponse = search(noChannelSession, "support");
        assertThat(noChannelResponse).hasStatus(HttpStatus.OK);
        assertThat(noChannelResponse.getResponse().getContentAsString())
                .contains("\"support\":null");
    }

    // TASKS.md item 124 (REQ-22, RAG)
    @Test
    void ragQueryOnlyMatchesTheCallersOwnConversations() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ22 Co"));
        User caller = member("req22-caller@example.com", tenant);
        User other = member("req22-other@example.com", tenant);
        ragConversation(tenant, caller, "Snorklewhistle Own");
        ragConversation(tenant, other, "Snorklewhistle Other");

        Cookie session = logIn("req22-caller@example.com");
        switchActiveTenant(session, tenant.getId());

        var response = search(session, "snorklewhistle");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("Snorklewhistle Own");
        assertThat(body).doesNotContain("Snorklewhistle Other");
    }

    // TASKS.md item 126 (REQ-18/AppSec, no oversight bypass)
    @Test
    void staffAdminWithNoAnchorsGetsZeroGroupAndRagResultsBeyondRealAnchors() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ18 Co"));
        User owner = member("req18-owner@example.com", tenant);
        group(tenant, "Plinkojumble Group", ChatGroupVisibility.PRIVATE);
        ragConversation(tenant, owner, "Plinkojumble Articles");

        User staffAdmin = staff("req18-staffadmin@example.com", GlobalRole.STAFF_ADMIN);
        Cookie session = logIn("req18-staffadmin@example.com");
        switchActiveTenant(session, tenant.getId());

        var response = search(session, "plinkojumble");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"groups\":{\"results\":[]");
        assertThat(body).contains("\"rag\":{\"results\":[]");
    }

    // TASKS.md item 128 (no-active-tenant fail-closed, all sections)
    @Test
    void noActiveTenantGetsEmptyGroupsSupportAndRagSectionsNotA500() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("NoActive Co"));
        User caller = member("noactive-caller@example.com", tenant);

        Cookie session = logIn("noactive-caller@example.com");

        var response = search(session, "anything");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"groups\":{\"results\":[]");
        assertThat(body).contains("\"support\":null");
        assertThat(body).contains("\"rag\":{\"results\":[]");
    }

    // TASKS.md item 130 (REQ-25/26, recent places)
    @Test
    void recentPlacesMergesChatAndRagConversationsExcludingLeftArchivedOrOtherUsers()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Recent Places Co"));
        User caller = member("recent-caller@example.com", tenant);
        User other = member("recent-other@example.com", tenant);

        ChatConversation stillIn = group(tenant, "Recent Still In", ChatGroupVisibility.PRIVATE);
        participate(stillIn, caller);

        ChatConversation left = group(tenant, "Recent Left", ChatGroupVisibility.PRIVATE);
        ChatParticipant leftParticipation =
                chatParticipantRepository.saveAndFlush(new ChatParticipant(left, caller));
        leftParticipation.setDeletedAt(java.time.Instant.now());
        chatParticipantRepository.saveAndFlush(leftParticipation);

        ragConversation(tenant, caller, "Recent Own Rag");
        ragConversation(tenant, other, "Recent Other Rag");

        Cookie session = logIn("recent-caller@example.com");
        switchActiveTenant(session, tenant.getId());

        var response = mockMvc.get().uri("/api/chat/search").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("Recent Still In", "Recent Own Rag");
        assertThat(body).doesNotContain("Recent Left");
        assertThat(body).doesNotContain("Recent Other Rag");
    }

    // TASKS.md item 132 (REQ-23, non-revealing omission)
    @Test
    void anInaccessibleMatchOfAnyKindReturnsTheSameShapeAsNoMatchAtAll() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ23 Co"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("REQ23 Other Co"));
        User caller = member("req23-caller@example.com", tenant);
        group(otherTenant, "Yonderquill Inaccessible", ChatGroupVisibility.PUBLIC);

        Cookie session = logIn("req23-caller@example.com");
        switchActiveTenant(session, tenant.getId());

        var responseForMatch = search(session, "yonderquill");
        var responseForNoMatch = search(session, "zzznomatchatall");

        assertThat(responseForMatch).hasStatus(HttpStatus.OK);
        assertThat(responseForNoMatch).hasStatus(HttpStatus.OK);
        assertThat(responseForMatch.getResponse().getContentAsString())
                .contains("\"groups\":{\"results\":[]");
        assertThat(responseForNoMatch.getResponse().getContentAsString())
                .contains("\"groups\":{\"results\":[]");
    }
}
