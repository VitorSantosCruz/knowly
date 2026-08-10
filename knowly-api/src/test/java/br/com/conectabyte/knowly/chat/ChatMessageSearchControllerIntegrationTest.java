package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.LoginCodeService;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
 * chat-message-search TASKS.md items 40/42/44/46-60: controller/HTTP-layer coverage, including the
 * SPEC's flagged main implementation risk (the core isolation test) and both AppSec-required
 * regression tests (cross-tenant, no-active-tenant fail-closed).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatMessageSearchControllerIntegrationTest {

    @Autowired private MockMvcTester mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private LoginCodeService loginCodeService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ChatConversationRepository chatConversationRepository;
    @Autowired private ChatParticipantRepository chatParticipantRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private SupportTicketRepository supportTicketRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @MockitoBean private JavaMailSender mailSender;

    @BeforeEach
    void resetLoginVelocityCounters() throws Exception {
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

    private void switchActiveTenant(Cookie session, Long tenantId) throws Exception {
        var response =
                mockMvc.post()
                        .uri("/api/tenants/active")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + "}")
                        .exchange();
        assertThat(response).hasStatus(HttpStatus.OK);
    }

    // /api/tenants/active/clear is not CSRF-exempt (only /api/tenants/active is) -- same convention
    // as TenantSessionIntegrationTest#obtainCsrfCookie()/staffClearingTheirActiveTenant...
    private Cookie obtainCsrfCookie() {
        return mockMvc.get()
                .uri("/actuator/health")
                .exchange()
                .getResponse()
                .getCookie("XSRF-TOKEN");
    }

    private void clearActiveTenant(Cookie session) throws Exception {
        Cookie csrf = obtainCsrfCookie();
        var response =
                mockMvc.post()
                        .uri("/api/tenants/active/clear")
                        .cookie(session)
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
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

    private ChatConversation conversation(ChatConversationKind kind, Tenant tenant, String title) {
        return chatConversationRepository.saveAndFlush(
                new ChatConversation(kind, tenant, title, null));
    }

    private void participate(ChatConversation conversation, User user) throws Exception {
        chatParticipantRepository.saveAndFlush(new ChatParticipant(conversation, user));
    }

    private void message(ChatConversation conversation, User sender, String content)
            throws Exception {
        chatMessageRepository.saveAndFlush(new ChatMessage(conversation, sender, content));
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult search(
            Cookie session, String query) {
        return mockMvc.get()
                .uri("/api/chat/messages/search")
                .param("q", query)
                .cookie(session)
                .exchange();
    }

    // TASKS.md item 40
    @Test
    void happyPathReturnsAPageDtoShapedBody() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Search Happy Path Co"));
        User caller = member("search-happy@example.com", tenant);
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "Happy Path Group");
        participate(conversation, caller);
        message(conversation, caller, "flumberjack happy path message");

        Cookie session = logIn("search-happy@example.com");

        var response = search(session, "flumberjack");

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"results\"");
        assertThat(response.getResponse().getContentAsString()).contains("flumberjack happy path");
    }

    // TASKS.md item 42
    @Test
    void blankQueryReturns400WithBlankQueryCode() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Search Blank Q Co"));
        member("search-blank-q@example.com", tenant);
        Cookie session = logIn("search-blank-q@example.com");

        var response = mockMvc.get().uri("/api/chat/messages/search").cookie(session).exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("CHAT_SEARCH_QUERY_BLANK");
    }

    @Test
    void dateFromAfterDateToReturns400WithInvalidDateRangeCode() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Search Bad Range Co"));
        member("search-bad-range@example.com", tenant);
        Cookie session = logIn("search-bad-range@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/chat/messages/search")
                        .param("q", "hello")
                        .param("dateFrom", "2026-08-09T00:00:00Z")
                        .param("dateTo", "2026-08-01T00:00:00Z")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString())
                .contains("CHAT_SEARCH_INVALID_DATE_RANGE");
    }

    @Test
    void malformedCursorReturns400WithExistingInvalidCursorCode() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Search Bad Cursor Co"));
        member("search-bad-cursor@example.com", tenant);
        Cookie session = logIn("search-bad-cursor@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/chat/messages/search")
                        .param("q", "hello")
                        .param("cursor", "not-base64-!!")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response.getResponse().getContentAsString()).contains("CHAT_INVALID_CURSOR");
    }

    // TASKS.md item 44 (REQ-3)
    @Test
    void
            conversationIdFilterPointingAtInaccessibleRealNonexistentSupportOrArchivedConversationsAllReturnEmptyIndistinguishableResults()
                    throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Search REQ3 Co"));
        User caller = member("search-req3-caller@example.com", tenant);
        User other = member("search-req3-other@example.com", tenant);

        // (a) real conversation the caller isn't a participant of
        ChatConversation notMine =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "Not Mine");
        participate(notMine, other);
        message(notMine, other, "wizzlecraft not mine message");

        // (b) nonexistent id
        Long nonexistentId = 9_999_999L;

        // (c) SUPPORT conversation
        ChatConversation support = conversation(ChatConversationKind.SUPPORT, tenant, "Support");
        participate(support, caller);
        message(support, caller, "wizzlecraft support message");

        // (d) archived former conversation of the caller's
        ChatConversation archived =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "Archived Former");
        participate(archived, caller);
        message(archived, caller, "wizzlecraft archived message");
        archived.setArchivedAt(Instant.now());
        chatConversationRepository.saveAndFlush(archived);

        Cookie session = logIn("search-req3-caller@example.com");

        List<Long> conversationIds =
                List.of(notMine.getId(), nonexistentId, support.getId(), archived.getId());
        for (Long conversationId : conversationIds) {
            var response =
                    mockMvc.get()
                            .uri("/api/chat/messages/search")
                            .param("q", "wizzlecraft")
                            .param("conversationId", String.valueOf(conversationId))
                            .cookie(session)
                            .exchange();

            assertThat(response).hasStatus(HttpStatus.OK);
            assertThat(response.getResponse().getContentAsString()).contains("\"results\":[]");
        }
    }

    // TASKS.md item 46: core isolation test, parameterized across removal modes
    private static Stream<Arguments> removalModes() {
        return Stream.of(
                Arguments.of("LEFT"),
                Arguments.of("REMOVED"),
                Arguments.of("ARCHIVED"),
                Arguments.of("SOFT_DELETED"));
    }

    @ParameterizedTest
    @MethodSource("removalModes")
    void formerParticipantsSearchExcludesTheRemovedConversationButStillSurfacesACurrentOne(
            String mode) throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Isolation Removal Co " + mode));
        User userA = member("isolation-a-" + mode.toLowerCase() + "@example.com", tenant);
        User userB = member("isolation-b-" + mode.toLowerCase() + "@example.com", tenant);

        ChatConversation shared =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "Shared " + mode);
        ChatParticipant participationA = new ChatParticipant(shared, userA);
        participationA = chatParticipantRepository.saveAndFlush(participationA);
        participate(shared, userB);
        message(shared, userB, "kerfuffleoxide shared message " + mode);

        ChatConversation still =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "Still Mine " + mode);
        participate(still, userA);
        message(still, userA, "kerfuffleoxide still mine message " + mode);

        switch (mode) {
            case "LEFT", "REMOVED" -> {
                participationA.setDeletedAt(Instant.now());
                chatParticipantRepository.saveAndFlush(participationA);
            }
            case "ARCHIVED" -> {
                shared.setArchivedAt(Instant.now());
                chatConversationRepository.saveAndFlush(shared);
            }
            case "SOFT_DELETED" -> {
                shared.setDeletedAt(Instant.now());
                chatConversationRepository.saveAndFlush(shared);
            }
            default -> throw new IllegalStateException("Unknown mode " + mode);
        }

        Cookie session = logIn("isolation-a-" + mode.toLowerCase() + "@example.com");

        var response = search(session, "kerfuffleoxide");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("still mine message " + mode);
        assertThat(body).doesNotContain("shared message " + mode);
    }

    // TASKS.md items 48/49/50: AppSec-required cross-tenant and no-active-tenant fail-closed
    @Test
    void searchWithTenantOneActiveReturnsOnlyTenantOnesMatchesNeverTenantTwos() throws Exception {
        Tenant tenantOne = tenantRepository.saveAndFlush(new Tenant("AppSec Tenant One"));
        Tenant tenantTwo = tenantRepository.saveAndFlush(new Tenant("AppSec Tenant Two"));
        User caller = userRepository.saveAndFlush(new User("appsec-two-tenant@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(caller, tenantOne, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(caller, tenantTwo, MembershipRole.MEMBER));

        ChatConversation conversationOne =
                conversation(ChatConversationKind.PEER_GROUP, tenantOne, "Tenant One Group");
        participate(conversationOne, caller);
        message(conversationOne, caller, "brontosaurustweak tenant one message");

        ChatConversation conversationTwo =
                conversation(ChatConversationKind.PEER_GROUP, tenantTwo, "Tenant Two Group");
        participate(conversationTwo, caller);
        message(conversationTwo, caller, "brontosaurustweak tenant two message");

        Cookie session = logIn("appsec-two-tenant@example.com");
        switchActiveTenant(session, tenantOne.getId());

        var response = search(session, "brontosaurustweak");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("tenant one message");
        assertThat(body).doesNotContain("tenant two message");
    }

    // AppSec follow-up (2026-08-10): the PO's isolation requirement applies to PEER_DIRECT (1:1)
    // conversations exactly as it does to PEER_GROUP -- BASE_PREDICATE/the scope fragments in
    // ChatMessageSearchRepository never branch on conversation kind, so this is a regression test
    // proving that structural guarantee end-to-end, not a gap that needed a code fix. A caller who
    // is a MEMBER of tenant X and also has a staff-scope (tenant-less) 1:1, and a 1:1 in tenant Y,
    // must see none of the staff-scope/tenant-Y content while active-tenant=X, and none of tenant
    // X's content while in staff scope (no active tenant).
    @Test
    void peerDirectConversationsAreIsolatedByActiveTenantAndStaffScopeJustLikeGroups()
            throws Exception {
        Tenant tenantX = tenantRepository.saveAndFlush(new Tenant("REQ5-PeerDirect Tenant X"));
        Tenant tenantY = tenantRepository.saveAndFlush(new Tenant("REQ5-PeerDirect Tenant Y"));

        // The caller is simultaneously a plain MEMBER of tenant X, a MEMBER of tenant Y, and a
        // (non-admin) STAFF user with a staff-scope (tenant-less) 1:1 -- exactly the PO's "one
        // person, three overlapping scopes" scenario. Deliberately plain STAFF, not STAFF_ADMIN:
        // STAFF_ADMIN always hits ChatMessageSearchService's REQ-5e PLATFORM_UNRESTRICTED branch
        // first, regardless of active tenant, which would make this test vacuous. Plain STAFF with
        // an active tenant hits the ordinary PARTICIPANT_AND_DISCOVERABLE branch bound to that
        // tenant (same as any MEMBER), and with no active tenant hits REQ-5f's staff-scope branch
        // --
        // exactly the two branches the PO's isolation requirement is about. The TENANT_ACT_AS_ANY
        // grant is only needed so this plain-STAFF session can use the session-switch endpoint
        // despite also holding a real membership in tenant X (see
        // TenantController#switchActiveTenant's staff path) -- it plays no role in the chat search
        // scoping under test.
        User caller = staff("peerdirect-caller@example.com", GlobalRole.STAFF);
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(caller, tenantX, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(caller, tenantY, MembershipRole.MEMBER));
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(caller, GlobalPermission.TENANT_ACT_AS_ANY));

        ChatConversation directInTenantX =
                conversation(ChatConversationKind.PEER_DIRECT, tenantX, null);
        participate(directInTenantX, caller);
        message(directInTenantX, caller, "hummingbirdlatch tenant x direct message");

        ChatConversation directInTenantY =
                conversation(ChatConversationKind.PEER_DIRECT, tenantY, null);
        participate(directInTenantY, caller);
        message(directInTenantY, caller, "hummingbirdlatch tenant y direct message");

        ChatConversation staffScopeDirect =
                conversation(ChatConversationKind.PEER_DIRECT, null, null);
        participate(staffScopeDirect, caller);
        message(staffScopeDirect, caller, "hummingbirdlatch staff scope direct message");

        Cookie session = logIn("peerdirect-caller@example.com");
        switchActiveTenant(session, tenantX.getId());

        var tenantXResponse = search(session, "hummingbirdlatch");
        assertThat(tenantXResponse).hasStatus(HttpStatus.OK);
        String tenantXBody = tenantXResponse.getResponse().getContentAsString();
        assertThat(tenantXBody).contains("tenant x direct message");
        assertThat(tenantXBody).doesNotContain("tenant y direct message");
        assertThat(tenantXBody).doesNotContain("staff scope direct message");

        clearActiveTenant(session);

        var staffScopeResponse = search(session, "hummingbirdlatch");
        assertThat(staffScopeResponse).hasStatus(HttpStatus.OK);
        String staffScopeBody = staffScopeResponse.getResponse().getContentAsString();
        assertThat(staffScopeBody).contains("staff scope direct message");
        assertThat(staffScopeBody).doesNotContain("tenant x direct message");
        assertThat(staffScopeBody).doesNotContain("tenant y direct message");
    }

    // REQ-5f: staff with no active tenant now gets scoped (participant) results, not an
    // unconditional empty result -- superseded by the role-based ruleset, still never a
    // cross-tenant scan.
    @Test
    void staffWithNoActiveTenantGetsResultsFromTheirOwnDirectParticipantConversation()
            throws Exception {
        User staffCaller = staff("req5f-staff-noactive@example.com", GlobalRole.STAFF);
        // Staff-scope (tenant-less) conversation -- must be visible with no active tenant.
        ChatConversation staffScopeConversation =
                conversation(ChatConversationKind.PEER_GROUP, null, "No Active Tenant Group");
        participate(staffScopeConversation, staffCaller);
        message(staffScopeConversation, staffCaller, "quibblesnort no active tenant message");

        // AppSec correction (2026-08-10): a tenant-owned conversation the same staff user
        // participates in (e.g. via an unrelated tenant membership) must NOT leak into the
        // staff-scope (no active tenant) result set -- staff-scope search is scoped to
        // cc.tenant_id IS NULL, not "any tenant the caller happens to be a participant of".
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ5f Tenant-Owned Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(staffCaller, tenant, MembershipRole.MEMBER));
        ChatConversation tenantOwnedConversation =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "Tenant-Owned Group");
        participate(tenantOwnedConversation, staffCaller);
        message(tenantOwnedConversation, staffCaller, "quibblesnort tenant-owned message");

        Cookie session = logIn("req5f-staff-noactive@example.com");

        var response = search(session, "quibblesnort");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("no active tenant message");
        assertThat(body).doesNotContain("tenant-owned message");
    }

    // REQ-5e: STAFF_ADMIN gets platform-wide unrestricted search, including a conversation they
    // hold no participant row on, with or without an active tenant.
    @Test
    void staffAdminWithNoActiveTenantGetsUnrestrictedResultsRegardlessOfParticipation()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ5e Staff Admin No Active Co"));
        User owner = member("req5e-owner@example.com", tenant);
        ChatConversation conversation =
                conversation(
                        ChatConversationKind.PEER_GROUP, tenant, "Staff Admin No Active Group");
        participate(conversation, owner);
        message(conversation, owner, "ratatouillewhisk staff admin no active message");

        staff("req5e-staffadmin-noactive@example.com", GlobalRole.STAFF_ADMIN);

        Cookie session = logIn("req5e-staffadmin-noactive@example.com");

        var response = search(session, "ratatouillewhisk");

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("ratatouillewhisk");
    }

    // TASKS.md items 51/52, superseded by REQ-5e/REQ-5g: STAFF_ADMIN/MEMBER_ADMIN now DO get
    // results from a conversation with zero participant rows, since their admin grant is an
    // explicit, bounded, unrestricted-within-scope privilege, not a REQ-2-style participancy
    // check.
    @Test
    void staffAdminWithZeroParticipantRowsStillGetsResultsUnrestricted() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ5e Staff Admin Co"));
        User owner = member("req5e-owner2@example.com", tenant);
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ5e Group");
        participate(conversation, owner);
        message(conversation, owner, "splendifantastic req5e message");

        User staffAdmin = staff("req5e-staffadmin@example.com", GlobalRole.STAFF_ADMIN);
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(staffAdmin, tenant, MembershipRole.MEMBER));
        Cookie session = logIn("req5e-staffadmin@example.com");
        switchActiveTenant(session, tenant.getId());

        var response = search(session, "splendifantastic");

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("splendifantastic");
    }

    @Test
    void memberAdminWithZeroParticipantRowsStillGetsResultsWithinTheirOwnActiveTenant()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ5g Member Admin Co"));
        User owner = member("req5g-owner@example.com", tenant);
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ5g Group");
        participate(conversation, owner);
        message(conversation, owner, "cantankerousfizzle req5g message");

        User memberAdmin = userRepository.saveAndFlush(new User("req5g-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(memberAdmin, tenant, MembershipRole.MEMBER_ADMIN));
        Cookie session = logIn("req5g-admin@example.com");

        var response = search(session, "cantankerousfizzle");

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("cantankerousfizzle");
    }

    // REQ-5j: a MEMBER_ADMIN's unrestricted grant is bounded to their own active tenant -- a
    // stale/unrelated participant row in a different tenant never becomes reachable, and a
    // conversation belonging to a different tenant is never returned.
    @Test
    void memberAdminUnrestrictedGrantNeverCrossesTenantBoundaries() throws Exception {
        Tenant tenantOne = tenantRepository.saveAndFlush(new Tenant("REQ5j Tenant One"));
        Tenant tenantTwo = tenantRepository.saveAndFlush(new Tenant("REQ5j Tenant Two"));

        User memberAdmin = userRepository.saveAndFlush(new User("req5j-admin@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(memberAdmin, tenantOne, MembershipRole.MEMBER_ADMIN));
        // Stale, unrelated participant row in tenant two -- must never widen the tenant-one
        // unrestricted grant into a cross-tenant scan.
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(memberAdmin, tenantTwo, MembershipRole.MEMBER));

        ChatConversation conversationOne =
                conversation(ChatConversationKind.PEER_GROUP, tenantOne, "REQ5j Tenant One Group");
        message(conversationOne, memberAdmin, "wafflequartz tenant one message");

        ChatConversation conversationTwo =
                conversation(ChatConversationKind.PEER_GROUP, tenantTwo, "REQ5j Tenant Two Group");
        participate(conversationTwo, memberAdmin);
        message(conversationTwo, memberAdmin, "wafflequartz tenant two message");

        Cookie session = logIn("req5j-admin@example.com");
        switchActiveTenant(session, tenantOne.getId());

        var response = search(session, "wafflequartz");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("tenant one message");
        assertThat(body).doesNotContain("tenant two message");
    }

    // REQ-5i: a non-admin's message search never matches a PRIVATE group they haven't joined,
    // even though it would surface for a PUBLIC/REQUEST_TO_JOIN group they haven't joined.
    @Test
    void nonAdminMemberNeverMatchesAnUnjoinedPrivateGroupButDoesMatchDiscoverableGroups()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ5i Discoverable Co"));
        member("req5i-caller@example.com", tenant);
        User other = member("req5i-other@example.com", tenant);

        ChatConversation privateGroup =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ5i Private Group");
        privateGroup.setVisibility(ChatGroupVisibility.PRIVATE);
        chatConversationRepository.saveAndFlush(privateGroup);
        participate(privateGroup, other);
        message(privateGroup, other, "puzzlewrenchgloam private group message");

        ChatConversation publicGroup =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ5i Public Group");
        publicGroup.setVisibility(ChatGroupVisibility.PUBLIC);
        chatConversationRepository.saveAndFlush(publicGroup);
        participate(publicGroup, other);
        message(publicGroup, other, "puzzlewrenchgloam public group message");

        Cookie session = logIn("req5i-caller@example.com");

        var response = search(session, "puzzlewrenchgloam");

        assertThat(response).hasStatus(HttpStatus.OK);
        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("public group message");
        assertThat(body).doesNotContain("private group message");
    }

    // TASKS.md items 53/54 (REQ-1)
    @Test
    void supportConversationMessageNeverAppearsInResultsEvenForTicketOwner() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ1 Support Co"));
        User owner = member("req1-owner@example.com", tenant);
        ChatConversation supportChannel =
                conversation(ChatConversationKind.SUPPORT, tenant, "Support Channel REQ1");
        supportChannel.setOwner(owner);
        chatConversationRepository.saveAndFlush(supportChannel);
        message(supportChannel, owner, "moonquillspatter support ticket message");
        supportTicketRepository.saveAndFlush(new SupportTicket(supportChannel));

        Cookie session = logIn("req1-owner@example.com");

        var response = search(session, "moonquillspatter");

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("\"results\":[]");
    }

    // TASKS.md items 55/56 (REQ-13/14, end to end)
    @Test
    void portugueseResolvedCallerMatchesAConjugatedFormEndToEnd() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ13 Locale Pt Co"));
        User caller = member("req13-pt@example.com", tenant);
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ13 Pt Group");
        participate(conversation, caller);
        message(conversation, caller, "os gatos correm rapido no jardim");

        Cookie session = logIn("req13-pt@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/chat/messages/search")
                        .param("q", "gato")
                        .header("Accept-Language", "pt-BR")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("gatos correm");
    }

    @Test
    void englishResolvedCallerMatchesAPluralFormEndToEnd() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ13 Locale En Co"));
        User caller = member("req13-en@example.com", tenant);
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ13 En Group");
        participate(conversation, caller);
        message(conversation, caller, "we have several meetings scheduled");

        Cookie session = logIn("req13-en@example.com");

        var response =
                mockMvc.get()
                        .uri("/api/chat/messages/search")
                        .param("q", "meeting")
                        .header("Accept-Language", "en-US")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("meetings scheduled");
    }

    @Test
    void aForgedLocaleShapedQueryParameterHasNoEffectOnWhichIndexIsQueried() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ14 Forged Locale Co"));
        User caller = member("req14-forged@example.com", tenant);
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ14 Forged Group");
        participate(conversation, caller);
        message(conversation, caller, "we have several meetings scheduled");

        Cookie session = logIn("req14-forged@example.com");

        // No Accept-Language header set (defaults to English per REQ-15) -- a forged "locale"
        // query param must have zero effect, since the endpoint never reads such a parameter at
        // all.
        var response =
                mockMvc.get()
                        .uri("/api/chat/messages/search")
                        .param("q", "meeting")
                        .param("locale", "pt-BR")
                        .cookie(session)
                        .exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response.getResponse().getContentAsString()).contains("meetings scheduled");
    }

    // TASKS.md items 57/58 (REQ-10)
    @Test
    void cursorPaginationAtTheControllerLayerHasNoOverlapOrGapsMostRecentFirst() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ10 Cursor Co"));
        User caller = member("req10-cursor@example.com", tenant);
        ChatConversation conversation =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ10 Group");
        participate(conversation, caller);
        for (int i = 0; i < 35; i++) {
            message(conversation, caller, "dandelionwarble message " + i);
        }

        Cookie session = logIn("req10-cursor@example.com");

        var page1 = search(session, "dandelionwarble");
        assertThat(page1).hasStatus(HttpStatus.OK);
        String nextCursor =
                com.jayway.jsonpath.JsonPath.read(
                        page1.getResponse().getContentAsString(), "$.nextCursor");
        assertThat(nextCursor).isNotNull();

        var page2 =
                mockMvc.get()
                        .uri("/api/chat/messages/search")
                        .param("q", "dandelionwarble")
                        .param("cursor", nextCursor)
                        .cookie(session)
                        .exchange();
        assertThat(page2).hasStatus(HttpStatus.OK);

        List<Integer> page1Ids =
                com.jayway.jsonpath.JsonPath.read(
                        page1.getResponse().getContentAsString(), "$.results[*].id");
        List<Integer> page2Ids =
                com.jayway.jsonpath.JsonPath.read(
                        page2.getResponse().getContentAsString(), "$.results[*].id");

        assertThat(page1Ids).hasSize(30);
        assertThat(page2Ids).hasSize(5);
        assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);
    }

    // TASKS.md item 59 (REQ-7/8/9)
    @Test
    void senderConversationAndDateRangeFiltersNarrowResultsAtTheControllerLayer() throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("REQ789 Co"));
        User caller = member("req789-caller@example.com", tenant);
        User other = member("req789-other@example.com", tenant);
        ChatConversation conversationOne =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ789 Group One");
        participate(conversationOne, caller);
        participate(conversationOne, other);
        ChatConversation conversationTwo =
                conversation(ChatConversationKind.PEER_GROUP, tenant, "REQ789 Group Two");
        participate(conversationTwo, caller);

        message(conversationOne, caller, "twizzlefunk from caller in one");
        message(conversationOne, other, "twizzlefunk from other in one");
        message(conversationTwo, caller, "twizzlefunk from caller in two");

        Cookie session = logIn("req789-caller@example.com");

        var bySender =
                mockMvc.get()
                        .uri("/api/chat/messages/search")
                        .param("q", "twizzlefunk")
                        .param("senderId", String.valueOf(other.getId()))
                        .cookie(session)
                        .exchange();
        assertThat(bySender).hasStatus(HttpStatus.OK);
        assertThat(bySender.getResponse().getContentAsString()).contains("from other in one");
        assertThat(bySender.getResponse().getContentAsString()).doesNotContain("from caller");

        var byConversation =
                mockMvc.get()
                        .uri("/api/chat/messages/search")
                        .param("q", "twizzlefunk")
                        .param("conversationId", String.valueOf(conversationTwo.getId()))
                        .cookie(session)
                        .exchange();
        assertThat(byConversation).hasStatus(HttpStatus.OK);
        assertThat(byConversation.getResponse().getContentAsString())
                .contains("from caller in two");
        assertThat(byConversation.getResponse().getContentAsString())
                .doesNotContain("from other in one");

        var futureRange =
                mockMvc.get()
                        .uri("/api/chat/messages/search")
                        .param("q", "twizzlefunk")
                        .param("dateFrom", Instant.now().plusSeconds(86400).toString())
                        .cookie(session)
                        .exchange();
        assertThat(futureRange).hasStatus(HttpStatus.OK);
        assertThat(futureRange.getResponse().getContentAsString()).contains("\"results\":[]");
    }
}
