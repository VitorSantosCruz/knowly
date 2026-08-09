import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ActiveTenantService } from './active-tenant.service';
import { ChatDirectoryService } from './chat-directory.service';
import { ChatGroupService } from './chat-group.service';
import { ChatGroupVisibility } from './chat.model';
import { ChatService } from './chat.service';
import { ConversationService } from './conversation.service';
import { ProfileService } from './profile.service';

export interface PersonRow {
  kind: 'person';
  key: string;
  userId: number;
  displayName: string;
  conversationId: number | null;
  /**
   * REQ-2's person rows should show the user's profile photo — `UserProfile.avatarUrl`
   * (`profile.service.ts`), rendered via `shared/avatar.component.ts`'s image/fallback pattern.
   * **Known gap (2026-08-09):** the backend's `CandidateUserDto`
   * (`GET /api/chat/eligible-participants`) only carries `{ userId, nickname }` — no
   * `avatarUrl` — so this is always `null` today, which safely renders `AvatarComponent`'s
   * generic fallback icon rather than a broken image. Wiring a real photo through requires a
   * backend DTO change (`CandidateUserDto` growing an `avatarUrl` field), which is out of this
   * purely-visual restructuring's scope — tracked here rather than silently faked or blocked
   * on, per this feature's "no new endpoint" constraint. Left as a follow-up.
   */
  avatarUrl: string | null;
}

export interface GroupRow {
  kind: 'group';
  key: string;
  id: number;
  displayName: string;
  visibility: ChatGroupVisibility | undefined;
  isMember: boolean;
}

/** REQ-2's Support row(s) — a single, always-present entry; opening it defers entirely to
 * `SupportPageComponent`'s existing own-channel/staff-inbox/browse permission dispatch
 * (unchanged), so this row never needs to enumerate individual tickets itself. */
export interface SupportRow {
  kind: 'support';
  key: 'support';
}

/** REQ-2's "Base de artigos" rows — every existing RAG conversation the viewer has. */
export interface ArticleRow {
  kind: 'article';
  key: string;
  id: number;
  displayName: string;
}

export type DirectoryRow = PersonRow | GroupRow | SupportRow | ArticleRow;

/** Sentinel distinguishing "never fetched" from "fetched for the no-active-tenant/staff-only
 * case" (`undefined`) — see `ChatDirectoryRowsService`'s `eligibleParticipantsFetchedForTenant`
 * doc comment. */
const NOT_FETCHED_YET = Symbol('not-fetched-yet');

/**
 * Shared engine backing both directory columns of Amendment (3)'s 3-column layout:
 * `chat-directory.component.ts` (column 1, `conversationRows` — one unified, Support-pinned
 * "CONVERSAS" list) and `chat-full-directory.component.ts` (column 3, `discoveryRows` — the
 * disjoint complement: not-yet-messaged people plus discoverable, non-member groups).
 *
 * History: originally split out (2026-08-09) to back a separate `ChatContactsPanelComponent`
 * third column; the product owner found that 3rd column redundant with column 1's own People
 * rows and asked for a "já falou"/"ainda não falou" partition to live *inside* column 1 instead
 * (same day) — briefly a 2-column shell. Amendment (3) (2026-08-09, later the same day)
 * supersedes that: column 1 becomes one unified list (`conversationRows`, Support pinned first)
 * and a real column 3 returns (`discoveryRows`), this time genuinely disjoint from column 1
 * rather than duplicating it. Kept as its own service throughout since it centralizes the
 * click-to-open-or-create/join/request-to-join logic in one place for both columns.
 */
@Injectable({ providedIn: 'root' })
export class ChatDirectoryRowsService {
  private readonly chatService = inject(ChatService);
  private readonly chatDirectoryService = inject(ChatDirectoryService);
  private readonly chatGroupService = inject(ChatGroupService);
  private readonly profileService = inject(ProfileService);
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly conversationService = inject(ConversationService);
  private readonly router = inject(Router);

  readonly rowErrors = signal<Set<string>>(new Set());
  readonly pendingGroupIds = signal<Set<number>>(new Set());
  private readonly articleConversations = signal<{ id: number; title: string | null }[]>([]);
  private readonly currentUserId = signal<number | null>(null);
  private loaded = false;
  private articlesFetchedForTenant: number | null = null;

  /**
   * Bug fix (2026-08-09, reported as a regression right after `chat-shell.component.ts`'s
   * "no-active-tenant flash" fix, though investigation showed that commit was unrelated —
   * this gap predates it): `ensureLoaded()` used to call
   * `chatService.fetchEligibleParticipants('direct')` **once**, with no `tenantId`, before
   * `ActiveTenantService` had resolved. A staff viewer working inside an active tenant has no
   * `TenantMembership` row for it (server-side session state only, per this codebase's
   * documented staff-session gotcha), so the backend's `direct`-scope eligibility check keeps
   * resolving that staff viewer to their staff-only anchor forever, regardless of which tenant
   * they're actually in — the "Haven't talked yet" people list (and, via the same
   * membership-based eligibility path, `ChatDirectoryService`'s discoverable-groups list) then
   * never reflects the active tenant's own people/groups. `undefined` (`unset`) as the initial
   * sentinel — as opposed to `null` — lets the very first, pre-resolution fetch (tenantId
   * `undefined`, i.e. staff-only) always happen once, then re-fires exactly once more when
   * `activeTenantResolved()` flips true, this time carrying the real `activeTenantId()` (or
   * still `undefined` for a genuine no-active-tenant session) — never flashing a third, wrong
   * state in between, mirroring `maybeFetchArticles()`'s own re-check-on-resolve shape below.
   */
  private eligibleParticipantsFetchedForTenant: number | undefined | typeof NOT_FETCHED_YET =
    NOT_FETCHED_YET;

  constructor() {
    // Reacts to ActiveTenantService.activeTenantId() resolving asynchronously (its own fetch()
    // call, triggered by ensureLoaded()) — a plain post-fetch call wouldn't see the resolved
    // value yet at that point since fetch() subscribes internally and doesn't block.
    effect(() => this.maybeFetchArticles());
    effect(() => this.maybeRefetchEligibleParticipants());
  }

  private readonly ownDirectConversations = computed(() =>
    this.chatService.conversations().filter((c) => c.kind === 'PEER_DIRECT'),
  );

  private readonly ownGroupConversations = computed(() =>
    this.chatService.conversations().filter((c) => c.kind === 'PEER_GROUP'),
  );

  /** People + Groups only (REQ-2b's contacts-column partitioning operates on this subset). */
  readonly personGroupRows = computed<(PersonRow | GroupRow)[]>(() => {
    const people: PersonRow[] = this.chatService.eligibleParticipants().map((candidate) => {
      const existing = this.ownDirectConversations().find((c) =>
        c.participantUserIds.includes(candidate.userId),
      );
      return {
        kind: 'person',
        key: `person:${candidate.userId}`,
        userId: candidate.userId,
        displayName: candidate.nickname,
        conversationId: existing?.id ?? null,
        avatarUrl: null,
      };
    });

    const ownGroups: GroupRow[] = this.ownGroupConversations().map((c) => ({
      kind: 'group',
      key: `group:${c.id}`,
      id: c.id,
      displayName: c.title ?? '',
      visibility: this.chatService.details().get(c.id)?.visibility,
      isMember: true,
    }));

    // REQ-19/28: the backend never returns a PRIVATE or already-joined group here — no
    // client-side re-filtering of that invariant (see ChatDirectoryService's own doc comment).
    const discoverableGroups: GroupRow[] = this.chatDirectoryService
      .discoverableGroups()
      .map((g) => ({
        kind: 'group',
        key: `group:${g.id}`,
        id: g.id,
        displayName: g.title,
        visibility: g.visibility,
        isMember: false,
      }));

    return [...people, ...ownGroups, ...discoverableGroups];
  });

  /**
   * People already messaged (has an existing 1:1 conversation) — "Already talked to", sorted
   * most-recently-active first (REQ-2's "sorted most-recently-active first", WhatsApp-style).
   *
   * **Known gap (2026-08-09)**: `ConversationSummary` carries no `lastMessageAt`/activity
   * timestamp (see this file's own model, and `chat.model.ts`'s existing doc comment on the
   * same gap) — true "last activity" ordering needs that backend field, tracked as a follow-up
   * alongside the `avatarUrl` gap above. Until then, this sorts by conversation id descending
   * (a newer id was created later) as the closest available proxy — **deliberately never** by
   * anything related to which row the viewer currently has open/selected, so merely opening a
   * conversation can never reorder the list (the exact flicker a tester reported: a row jumping
   * to the top on click, then "jumping back" once not the active view anymore, because nothing
   * about *viewing* a conversation is itself an activity event).
   */
  readonly talkedPeople = computed<PersonRow[]>(() =>
    this.personGroupRows()
      .filter((row): row is PersonRow => row.kind === 'person' && row.conversationId !== null)
      .sort((a, b) => (b.conversationId ?? 0) - (a.conversationId ?? 0)),
  );

  /**
   * People eligible but not yet messaged — feeds `discoveryRows()` (column 3), not rendered
   * directly by any component (Amendment (3), task 138: `personGroupRows()`'s combined shape is
   * retired in favor of the narrower computeds feeding `conversationRows`/`discoveryRows`
   * directly).
   */
  private readonly notTalkedPeople = computed<PersonRow[]>(() =>
    this.personGroupRows().filter(
      (row): row is PersonRow => row.kind === 'person' && row.conversationId === null,
    ),
  );

  /**
   * Groups the viewer already participates in — feeds `conversationRows()` (column 1).
   * Same id-descending recency proxy as `talkedPeople` above, same known gap/rationale.
   * **Amendment (3), task 138:** narrowed to member groups only — discoverable, non-member
   * groups moved out to `discoverableGroupRows` below, feeding `discoveryRows()` (column 3)
   * instead of this computed.
   */
  readonly groupRows = computed<GroupRow[]>(() =>
    this.personGroupRows()
      .filter((row): row is GroupRow => row.kind === 'group' && row.isMember)
      .sort((a, b) => b.id - a.id),
  );

  /** Discoverable groups the viewer is **not** yet a participant of — feeds `discoveryRows()`
   * (column 3), per REQ-2d's disjoint-complement rule. */
  private readonly discoverableGroupRows = computed<GroupRow[]>(() =>
    this.personGroupRows().filter((row): row is GroupRow => row.kind === 'group' && !row.isMember),
  );

  readonly supportRow: SupportRow = { kind: 'support', key: 'support' };

  /** Same id-descending recency proxy as `talkedPeople`/`groupRows` above — not currently
   * sorted at all before Amendment (3), a small extension rather than a new concept. */
  readonly articleRows = computed<ArticleRow[]>(() =>
    this.articleConversations()
      .map((c) => ({
        kind: 'article' as const,
        key: `article:${c.id}`,
        id: c.id,
        displayName: c.title ?? '',
      }))
      .sort((a, b) => b.id - a.id),
  );

  /**
   * Column 1's unified "CONVERSAS" list (REQ-1/REQ-2, Amended (3), final): Support
   * unconditionally first, then every conversation the viewer already has — people already
   * messaged, groups already joined, and every existing "Base de artigos" conversation — each
   * kind sorted by its own already-documented recency proxy (see `talkedPeople`/`groupRows`/
   * `articleRows`'s own doc comments). This is a thin `computed()` that only concatenates
   * already-sorted per-kind lists, deliberately not a single flat sort, so each kind's own known
   * gap/proxy stays documented at the computed that owns it rather than smeared into one
   * function body.
   */
  readonly conversationRows = computed<DirectoryRow[]>(() => [
    this.supportRow,
    ...this.talkedPeople(),
    ...this.groupRows(),
    ...this.articleRows(),
  ]);

  /**
   * Column 3's full-directory list (REQ-2d, Amended (3), final) — the disjoint complement of
   * `conversationRows()`: people with no existing 1:1 conversation, plus every discoverable
   * group the viewer isn't a participant of.
   *
   * **Sort — interim fallback (2026-08-09), not the real REQ-2d ranking:** alphabetical by
   * `displayName`. REQ-2d's actual sort (descending by cross-surface last-interaction
   * timestamp, survivable across a hard-deleted 1:1) needs a new backend endpoint that does not
   * exist yet (`GET /api/chat/interaction-recency`-style, see `PLAN.md`'s "Cross-surface
   * recency sort" feasibility decision) — TASKS.md tracks the real sort as BLOCKED (tasks
   * 141-142). Every row here is treated as "no known interaction" for now, which is exactly
   * REQ-2d's own documented tiebreak for that case, not a placeholder standing in for an
   * already-computed real value.
   */
  readonly discoveryRows = computed<(PersonRow | GroupRow)[]>(() =>
    [...this.notTalkedPeople(), ...this.discoverableGroupRows()].sort((a, b) =>
      a.displayName.localeCompare(b.displayName),
    ),
  );

  /** Fetches every data source this list needs — idempotent, safe to call from more than one
   * host component (`ChatDirectoryComponent` and `ChatContactsPanelComponent` both call it). */
  ensureLoaded(): void {
    if (this.loaded) {
      return;
    }
    this.loaded = true;
    this.chatService.fetchConversations();
    this.chatDirectoryService.fetchDiscoverableGroups();
    this.profileService
      .getOwnProfile()
      .subscribe((profile) => this.currentUserId.set(profile.userId));
    this.activeTenantService.fetch();
    this.maybeRefetchEligibleParticipants();
    this.maybeFetchArticles();
  }

  /**
   * See `eligibleParticipantsFetchedForTenant`'s doc comment above. Fires once immediately
   * (from `ensureLoaded()`, before `ActiveTenantService` has resolved — `activeTenantId()`
   * reads `null` at that point, so this fetches with `tenantId: undefined`, same as before)
   * and again exactly once more when `activeTenantResolved()` flips `true`, this time with the
   * real `activeTenantId()` (still `undefined` if genuinely no active tenant) — never a 3rd,
   * stale re-fetch after that, and never skipped just because the unresolved fetch already ran.
   */
  private maybeRefetchEligibleParticipants(): void {
    // Both signals must be read unconditionally, before the `loaded` early-return below, so
    // Angular's effect keeps tracking them as dependencies even on a run that skips (e.g. the
    // effect's own guaranteed first run, which always fires before `ensureLoaded()` has set
    // `loaded`) — otherwise this effect would never see `activeTenantId()`/
    // `activeTenantResolved()` change and would silently stop reacting to the real tenant
    // resolving.
    const resolved = this.activeTenantService.activeTenantResolved();
    const tenantId = resolved
      ? (this.activeTenantService.activeTenantId() ?? undefined)
      : undefined;
    if (!this.loaded) {
      return;
    }
    if (this.eligibleParticipantsFetchedForTenant === tenantId) {
      return;
    }
    this.eligibleParticipantsFetchedForTenant = tenantId;
    this.chatService.fetchEligibleParticipants('direct', tenantId);
  }

  /** Re-checked on every call (cheap, in-memory) since `ActiveTenantService.activeTenantId()`
   * may resolve asynchronously after `ensureLoaded()` already ran once. */
  maybeFetchArticles(): void {
    const tenantId = this.activeTenantService.activeTenantId();
    if (tenantId === null || this.articlesFetchedForTenant === tenantId) {
      return;
    }
    this.articlesFetchedForTenant = tenantId;
    this.conversationService
      .list(tenantId)
      .subscribe((conversations) => this.articleConversations.set(conversations));
  }

  onPersonClick(row: PersonRow): void {
    this.clearRowError(row.key);
    if (row.conversationId !== null) {
      this.router.navigate(['/chat', row.conversationId]);
      return;
    }
    this.chatService
      .createConversation({ kind: 'DIRECT', participantUserIds: [row.userId] })
      .subscribe({
        next: (conversation) => this.router.navigate(['/chat', conversation.id]),
        error: () => this.setRowError(row.key),
      });
  }

  onGroupClick(row: GroupRow): void {
    this.clearRowError(row.key);
    if (row.isMember) {
      this.router.navigate(['/chat', row.id]);
      return;
    }

    if (row.visibility === 'PUBLIC') {
      this.chatGroupService.join(row.id).subscribe({
        next: () => this.router.navigate(['/chat', row.id]),
        error: () => this.setRowError(row.key),
      });
      return;
    }

    if (row.visibility === 'REQUEST_TO_JOIN') {
      this.chatGroupService.requestToJoin(row.id).subscribe({
        next: () => this.pendingGroupIds.update((ids) => new Set(ids).add(row.id)),
        error: () => this.setRowError(row.key),
      });
    }
  }

  onSupportClick(): void {
    this.router.navigate(['/chat'], { queryParams: { section: 'support' } });
  }

  onArticleClick(row: ArticleRow): void {
    this.router.navigate(['/chat/articles', row.id]);
  }

  private setRowError(key: string): void {
    this.rowErrors.update((errors) => new Set(errors).add(key));
  }

  private clearRowError(key: string): void {
    this.rowErrors.update((errors) => {
      if (!errors.has(key)) {
        return errors;
      }
      const next = new Set(errors);
      next.delete(key);
      return next;
    });
  }
}
