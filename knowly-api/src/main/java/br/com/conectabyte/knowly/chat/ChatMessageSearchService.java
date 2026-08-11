package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.chat.dto.ChatMessageSearchPageDto;
import br.com.conectabyte.knowly.chat.dto.ChatMessageSearchResultDto;
import br.com.conectabyte.knowly.chat.exception.ChatBlankSearchQueryException;
import br.com.conectabyte.knowly.chat.exception.ChatInvalidSearchDateRangeException;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-1 through REQ-15, REQ-5e-REQ-5j: orchestrates chat-message-search -- input validation
 * (REQ-11/12), locale resolution (REQ-13-15), role-based scope resolution (REQ-5e-REQ-5j), and
 * delegation to {@link ChatMessageSearchRepository}.
 *
 * <p><b>Role-based scoping (2026-08-10 amendment, context-boundary correction -- supersedes the
 * original "never reads {@code isStaff()}/{@code isStaffAdmin()}" no-bypass posture for this
 * method, and supersedes this same amendment's own first (buggy) draft).</b> The confirmed bug: the
 * v1 draft checked {@code tenantContext.isStaffAdmin()} <i>before</i> resolving the caller's
 * current context, so a {@code STAFF_ADMIN} viewing chat in staff scope could match a tenant
 * member's message. The fix resolves context <i>first</i>, then branches admin-vs-non-admin
 * strictly within that resolved context, per SPEC.md's "Amended (2026-08-10, context-boundary
 * correction) -- REQ-5 completion (final)":
 *
 * <ol>
 *   <li>Resolve {@code activeTenantId = tenantContext.getActiveTenantId()} first, unconditionally,
 *       before any role check.
 *   <li>If {@code activeTenantId} is present (tenant-X context):
 *       <ol type="a">
 *         <li>the caller is that tenant's active {@code MEMBER_ADMIN} -&gt; unrestricted within
 *             that tenant only (REQ-5g), never cross-tenant, never staff scope (REQ-5j).
 *         <li>else -&gt; participant + discoverable-group scope, bound to that tenant (REQ-5h/
 *             REQ-5i), including the AppSec-required {@code IllegalStateException} invariant on a
 *             null tenant id.
 *       </ol>
 *   <li>Else ({@code activeTenantId} empty, staff-scope context):
 *       <ol type="a">
 *         <li>{@code tenantContext.isStaffAdmin()} -&gt; unrestricted within staff scope only
 *             (REQ-5e, corrected) -- reachable only when there is no active tenant, so a {@code
 *             STAFF_ADMIN} can no longer short-circuit into a tenant's content.
 *         <li>else {@code tenantContext.isStaff()} -&gt; participant + discoverable-group scope,
 *             unbound to any tenant -- the staff-chat-parity fix (REQ-5f).
 *         <li>else -&gt; fail closed: empty result, no query executed at all -- REQ-2's original
 *             baseline for the one caller shape this amendment doesn't touch.
 *       </ol>
 * </ol>
 *
 * The active tenant id and role state are both re-derived fresh, per request, from {@link
 * TenantContext}/{@link TenantMembershipRepository} -- never cached, never client-supplied.
 */
@Service
public class ChatMessageSearchService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageSearchService.class);

    private final ChatMessageSearchRepository chatMessageSearchRepository;
    private final ChatMessageSearchLocaleResolver chatMessageSearchLocaleResolver;
    private final TenantContext tenantContext;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatEligibilityService chatEligibilityService;
    private final GlobalPermissionService globalPermissionService;

    public ChatMessageSearchService(
            ChatMessageSearchRepository chatMessageSearchRepository,
            ChatMessageSearchLocaleResolver chatMessageSearchLocaleResolver,
            TenantContext tenantContext,
            TenantMembershipRepository tenantMembershipRepository,
            ChatConversationRepository chatConversationRepository,
            ChatEligibilityService chatEligibilityService,
            GlobalPermissionService globalPermissionService) {
        this.chatMessageSearchRepository = chatMessageSearchRepository;
        this.chatMessageSearchLocaleResolver = chatMessageSearchLocaleResolver;
        this.tenantContext = tenantContext;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.chatConversationRepository = chatConversationRepository;
        this.chatEligibilityService = chatEligibilityService;
        this.globalPermissionService = globalPermissionService;
    }

    @Transactional(readOnly = true)
    public ChatMessageSearchPageDto search(
            User actor,
            String q,
            Long senderId,
            Long conversationId,
            Instant dateFrom,
            Instant dateTo,
            String cursor,
            Integer size,
            String acceptLanguageHeader) {
        if (q == null || q.isBlank()) {
            throw new ChatBlankSearchQueryException();
        }

        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ChatInvalidSearchDateRangeException();
        }

        Optional<Long> activeTenantId = tenantContext.getActiveTenantId();
        int pageSize = ChatCursor.clampSize(size);
        Long decodedCursor = cursor == null ? null : ChatCursor.decode(cursor);
        ChatSearchLocale locale = chatMessageSearchLocaleResolver.resolve(acceptLanguageHeader);

        List<ChatMessageSearchRepository.ChatMessageSearchRow> rows;

        if (activeTenantId.isPresent()) {
            // Tenant-X context: resolve admin-vs-non-admin strictly within this tenant.
            Long tenantId = activeTenantId.get();
            if (tenantId == null) {
                // AppSec-required invariant: must never happen -- Optional#isPresent() already
                // guarantees a non-null value here. Guards against a future refactor of the
                // precedence chain silently falling through into the nullable-aware
                // PARTICIPANT_AND_DISCOVERABLE fragment with a null id, which would widen this
                // branch (an ordinary tenant MEMBER) to the staff-scope-unrestricted branch's
                // legitimate null-tenant case is allowed to hit.
                throw new IllegalStateException(
                        "activeTenantId must be non-null when TenantContext#getActiveTenantId()"
                                + " reports present");
            }

            Optional<TenantMembership> activeMembership = activeMembershipIn(actor, tenantId);

            if (activeMembership.isPresent()) {
                if (activeMembership.get().getRole() == MembershipRole.MEMBER_ADMIN) {
                    // REQ-5g/REQ-5j: TENANT_UNRESTRICTED, bound to the caller's own active tenant
                    // only.
                    rows =
                            tenantUnrestrictedSearch(
                                    actor.getId(),
                                    tenantId,
                                    q,
                                    senderId,
                                    conversationId,
                                    dateFrom,
                                    dateTo,
                                    decodedCursor,
                                    pageSize,
                                    locale);
                } else {
                    // REQ-5h/REQ-5i: PARTICIPANT_AND_DISCOVERABLE, bound to the caller's active
                    // tenant -- membership (even a plain MEMBER) overrides any staff role.
                    rows =
                            scopedSearch(
                                    actor,
                                    tenantId,
                                    q,
                                    senderId,
                                    conversationId,
                                    dateFrom,
                                    dateTo,
                                    decodedCursor,
                                    pageSize,
                                    locale);
                }
            } else if (tenantContext.isStaffAdmin()
                    || (tenantContext.isStaff()
                            && globalPermissionService.hasPermission(
                                    actor, GlobalPermission.TENANT_ACT_AS_ANY))) {
                // REQ-5s(a)/REQ-5s(b)/REQ-5t: no membership in the active tenant -- a STAFF_ADMIN,
                // or a non-admin STAFF holding TENANT_ACT_AS_ANY, gets the same TENANT_UNRESTRICTED
                // fragment a MEMBER_ADMIN would.
                rows =
                        tenantUnrestrictedSearch(
                                actor.getId(),
                                tenantId,
                                q,
                                senderId,
                                conversationId,
                                dateFrom,
                                dateTo,
                                decodedCursor,
                                pageSize,
                                locale);
            } else {
                // REQ-5s(e): no membership, not STAFF_ADMIN, STAFF without TENANT_ACT_AS_ANY --
                // fail closed, mirroring the no-active-tenant/not-staff branch exactly.
                logSearch(actor, senderId, conversationId, dateFrom, dateTo, 0);
                return new ChatMessageSearchPageDto(List.of(), null);
            }
        } else if (tenantContext.isStaffAdmin()) {
            // REQ-5e (corrected): STAFF_SCOPE_UNRESTRICTED -- only reachable here, with no active
            // tenant, so this branch can never see a tenant-owned conversation.
            rows =
                    locale == ChatSearchLocale.PT
                            ? chatMessageSearchRepository.searchStaffScopeUnrestrictedPt(
                                    q,
                                    senderId,
                                    conversationId,
                                    dateFrom,
                                    dateTo,
                                    decodedCursor,
                                    pageSize,
                                    actor.getId())
                            : chatMessageSearchRepository.searchStaffScopeUnrestrictedEn(
                                    q,
                                    senderId,
                                    conversationId,
                                    dateFrom,
                                    dateTo,
                                    decodedCursor,
                                    pageSize,
                                    actor.getId());
        } else if (tenantContext.isStaff()) {
            // REQ-5f: PARTICIPANT_AND_DISCOVERABLE, unbound to any tenant (staff-chat parity).
            Long[] additionalVisibleConversationIds =
                    additionalVisibleConversationIdsStaffScope(actor);
            rows =
                    locale == ChatSearchLocale.PT
                            ? chatMessageSearchRepository.searchScopedPt(
                                    actor.getId(),
                                    null,
                                    additionalVisibleConversationIds,
                                    q,
                                    senderId,
                                    conversationId,
                                    dateFrom,
                                    dateTo,
                                    decodedCursor,
                                    pageSize)
                            : chatMessageSearchRepository.searchScopedEn(
                                    actor.getId(),
                                    null,
                                    additionalVisibleConversationIds,
                                    q,
                                    senderId,
                                    conversationId,
                                    dateFrom,
                                    dateTo,
                                    decodedCursor,
                                    pageSize);
        } else {
            // Fail closed: no active tenant, not staff -- REQ-2's original baseline.
            logSearch(actor, senderId, conversationId, dateFrom, dateTo, 0);
            return new ChatMessageSearchPageDto(List.of(), null);
        }

        List<ChatMessageSearchResultDto> results =
                rows.stream()
                        .map(
                                row ->
                                        new ChatMessageSearchResultDto(
                                                row.getId(),
                                                row.getConversationId(),
                                                row.getConversationTitle(),
                                                row.getSenderUserId(),
                                                row.getSenderNickname(),
                                                row.getContent(),
                                                row.getCreatedAt(),
                                                row.getIsParticipant(),
                                                row.getVisibility()))
                        .toList();

        String nextCursor =
                rows.size() < pageSize
                        ? null
                        : ChatCursor.encode(rows.get(rows.size() - 1).getId());

        logSearch(actor, senderId, conversationId, dateFrom, dateTo, results.size());

        return new ChatMessageSearchPageDto(results, nextCursor);
    }

    /**
     * REQ-5s: resolves the caller's active {@link TenantMembership} in {@code tenantId}, if any --
     * membership presence (any role, not just {@code MEMBER_ADMIN}) gates the entire branch,
     * re-derived fresh per request rather than trusted from a cached/client-supplied role
     * assertion.
     */
    private Optional<TenantMembership> activeMembershipIn(User actor, Long tenantId) {
        return tenantMembershipRepository.findByUserAndActiveTrue(actor).stream()
                .filter(TenantMembership::isActive)
                .filter(m -> m.getTenant().getId().equals(tenantId))
                .findFirst();
    }

    /** REQ-5g/REQ-5j/REQ-5s(a)/REQ-5s(b): TENANT_UNRESTRICTED, bound to {@code tenantId}. */
    private List<ChatMessageSearchRepository.ChatMessageSearchRow> tenantUnrestrictedSearch(
            Long callerId,
            Long tenantId,
            String q,
            Long senderId,
            Long conversationId,
            Instant dateFrom,
            Instant dateTo,
            Long decodedCursor,
            int pageSize,
            ChatSearchLocale locale) {
        return locale == ChatSearchLocale.PT
                ? chatMessageSearchRepository.searchTenantUnrestrictedPt(
                        tenantId,
                        q,
                        senderId,
                        conversationId,
                        dateFrom,
                        dateTo,
                        decodedCursor,
                        pageSize,
                        callerId)
                : chatMessageSearchRepository.searchTenantUnrestrictedEn(
                        tenantId,
                        q,
                        senderId,
                        conversationId,
                        dateFrom,
                        dateTo,
                        decodedCursor,
                        pageSize,
                        callerId);
    }

    /** REQ-5h/REQ-5i: PARTICIPANT_AND_DISCOVERABLE, bound to {@code tenantId}. */
    private List<ChatMessageSearchRepository.ChatMessageSearchRow> scopedSearch(
            User actor,
            Long tenantId,
            String q,
            Long senderId,
            Long conversationId,
            Instant dateFrom,
            Instant dateTo,
            Long decodedCursor,
            int pageSize,
            ChatSearchLocale locale) {
        Long[] additionalVisibleConversationIds = additionalVisibleConversationIds(actor, tenantId);
        return locale == ChatSearchLocale.PT
                ? chatMessageSearchRepository.searchScopedPt(
                        actor.getId(),
                        tenantId,
                        additionalVisibleConversationIds,
                        q,
                        senderId,
                        conversationId,
                        dateFrom,
                        dateTo,
                        decodedCursor,
                        pageSize)
                : chatMessageSearchRepository.searchScopedEn(
                        actor.getId(),
                        tenantId,
                        additionalVisibleConversationIds,
                        q,
                        senderId,
                        conversationId,
                        dateFrom,
                        dateTo,
                        decodedCursor,
                        pageSize);
    }

    /**
     * REQ-5h/REQ-5i/REQ-19: non-participant {@code PUBLIC}/{@code REQUEST_TO_JOIN} groups the
     * caller is {@link ChatEligibilityService}-eligible for, within the given tenant -- the same
     * discoverability set {@code ChatConversationService#listDiscoverableGroups} already exposes
     * for browsing, reused rather than reimplemented. {@code PRIVATE} groups are never included
     * (REQ-5i), since {@code findDiscoverableIds}'s own visibility predicate already excludes them.
     */
    private Long[] additionalVisibleConversationIds(User actor, Long tenantId) {
        return resolveEligibleDiscoverableIds(
                actor, chatConversationRepository.findDiscoverableIds(tenantId));
    }

    /** REQ-5f: staff-scope sibling of {@link #additionalVisibleConversationIds(User, Long)}. */
    private Long[] additionalVisibleConversationIdsStaffScope(User actor) {
        return resolveEligibleDiscoverableIds(
                actor, chatConversationRepository.findDiscoverableIdsStaffScope());
    }

    private Long[] resolveEligibleDiscoverableIds(User actor, List<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
            return new Long[0];
        }

        return chatConversationRepository.findByIdIn(candidateIds).stream()
                .filter(c -> chatEligibilityService.isEligible(actor, tenantAnchorOf(c)))
                .map(ChatConversation::getId)
                .toArray(Long[]::new);
    }

    private Long tenantAnchorOf(ChatConversation conversation) {
        return conversation.getTenant() == null ? null : conversation.getTenant().getId();
    }

    /**
     * Structured, non-{@code @AuditLog} logging per PLAN.md's non-functional requirements: actor
     * id, {@code hasQuery}, filter-presence booleans, and result count only. Reminder for future
     * contributors: never add the raw {@code q} string to this log line -- it is user-authored free
     * text that may contain sensitive content.
     */
    private void logSearch(
            User actor,
            Long senderId,
            Long conversationId,
            Instant dateFrom,
            Instant dateTo,
            int resultCount) {
        log.info(
                "chat.message.search actorId={} hasQuery={} hasSenderFilter={} hasConversationFilter={}"
                        + " hasDateRangeFilter={} resultCount={}",
                actor.getId(),
                true,
                senderId != null,
                conversationId != null,
                dateFrom != null || dateTo != null,
                resultCount);
    }
}
