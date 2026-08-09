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

/**
 * Shared engine backing `chat-directory.component.ts` (the directory column's unified,
 * searchable list) — REQ-2. Originally split out (2026-08-09) to also back a separate
 * `ChatContactsPanelComponent` third column; the product owner then found a 3rd column
 * redundant with column 1 and asked for that same "já falou"/"ainda não falou" partitioning
 * idea to *replace* the People section's flat list instead (2026-08-09, same day) — so the
 * shell stays 2 columns (directory, conversation), and `talkedPeople`/`notTalkedPeople` below
 * are consumed directly by `ChatDirectoryComponent`, not a separate panel. Kept as its own
 * service anyway (not inlined back into the component) since it still centralizes the
 * click-to-open-or-create/join/request-to-join logic in one place.
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

  constructor() {
    // Reacts to ActiveTenantService.activeTenantId() resolving asynchronously (its own fetch()
    // call, triggered by ensureLoaded()) — a plain post-fetch call wouldn't see the resolved
    // value yet at that point since fetch() subscribes internally and doesn't block.
    effect(() => this.maybeFetchArticles());
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

  /** People eligible but not yet messaged — "Haven't talked yet" (no conversation to rank by
   * recency, so this keeps the backend's own eligible-participants order). */
  readonly notTalkedPeople = computed<PersonRow[]>(() =>
    this.personGroupRows().filter(
      (row): row is PersonRow => row.kind === 'person' && row.conversationId === null,
    ),
  );

  /** Same id-descending recency proxy as `talkedPeople` above, same known gap/rationale. */
  readonly groupRows = computed<GroupRow[]>(() =>
    this.personGroupRows()
      .filter((row): row is GroupRow => row.kind === 'group')
      .sort((a, b) => b.id - a.id),
  );

  readonly supportRow: SupportRow = { kind: 'support', key: 'support' };

  readonly articleRows = computed<ArticleRow[]>(() =>
    this.articleConversations().map((c) => ({
      kind: 'article',
      key: `article:${c.id}`,
      id: c.id,
      displayName: c.title ?? '',
    })),
  );

  /** Full unified list (REQ-2): person/group rows plus the always-present Support row and
   * every existing "Base de artigos" conversation. */
  readonly rows = computed<DirectoryRow[]>(() => [
    ...this.personGroupRows(),
    this.supportRow,
    ...this.articleRows(),
  ]);

  /** Fetches every data source this list needs — idempotent, safe to call from more than one
   * host component (`ChatDirectoryComponent` and `ChatContactsPanelComponent` both call it). */
  ensureLoaded(): void {
    if (this.loaded) {
      return;
    }
    this.loaded = true;
    this.chatService.fetchConversations();
    this.chatService.fetchEligibleParticipants('direct');
    this.chatDirectoryService.fetchDiscoverableGroups();
    this.profileService
      .getOwnProfile()
      .subscribe((profile) => this.currentUserId.set(profile.userId));
    this.activeTenantService.fetch();
    this.maybeFetchArticles();
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
