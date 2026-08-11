import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { interval } from 'rxjs';
import { ChatService } from '../../core/chat.service';
import { deriveViewerRelation } from '../../core/chat.model';
import { ProfileService } from '../../core/profile.service';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { MessageThreadComponent } from '../../shared/chat/message-thread.component';
import { ChatHeaderComponent } from './chat-header.component';
import { GroupInfoModalComponent } from './group-info-modal.component';
import { PersonInfoModalComponent } from './person-info-modal.component';

const POLL_INTERVAL_MS = 5000;

/**
 * Route: `/chat/:conversationId`. REQ-4/5/6/7/8/9/19/20/21 — composer omitted entirely for a
 * `viewerRelation === 'LOOKING_IN'` look-in viewer (out-of-scope composer for oversight-only
 * presence), polling gated by `document.visibilityState`.
 *
 * **2026-08-09 UX follow-up**: the "who/what is this conversation with" details (a 1:1
 * participant's profile, or a group's full administration panel — visibility/members/
 * invite/delete/leave) used to sit always-visible under the header. They now live behind the
 * header's own icon+name click, in one of `PersonInfoModalComponent`/`GroupInfoModalComponent`
 * (see those files) — this component only tracks which one is open and passes through the data
 * they need; the message thread stays exactly where it was.
 */
@Component({
  selector: 'app-conversation-detail',
  imports: [
    ChatHeaderComponent,
    MessageThreadComponent,
    NoAccessStateComponent,
    PersonInfoModalComponent,
    GroupInfoModalComponent,
  ],
  template: `
    @if (chatService.detailErrors().has(conversationId())) {
      <app-no-access-state />
    } @else if (detail(); as detail) {
      <div class="flex h-full min-h-0 flex-col gap-3">
        <div class="shrink-0">
          <app-chat-header
            [detail]="detail"
            [viewerRelation]="viewerRelation()!"
            [currentUserId]="currentUserId()"
            (openInfo)="infoModalOpen.set(true)"
          />

          @if (detail.kind === 'PEER_GROUP') {
            <app-group-info-modal
              [open]="infoModalOpen()"
              [detail]="detail"
              [currentUserId]="currentUserId()"
              (dismissed)="infoModalOpen.set(false)"
            />
          } @else {
            <app-person-info-modal
              [open]="infoModalOpen()"
              [userId]="otherParticipantId()"
              (dismissed)="infoModalOpen.set(false)"
            />
          }
        </div>

        <app-message-thread
          [messages]="displayMessages()"
          [hasMore]="entry().hasMore"
          [loading]="entry().loading"
          [loadError]="entry().loadError"
          [showComposer]="viewerRelation() === 'PARTICIPANT'"
          [highlightMessageId]="jumpTargetMessageId()"
          [highlightQuery]="jumpToQuery()"
          (loadMore)="chatService.loadOlderMessages(conversationId())"
          (send)="onSend($event)"
          (retry)="onRetry($event)"
        />
      </div>
    }
  `,
  // See ChatShellComponent's :host comment — same reasoning applies to every component nested
  // inside chat-shell's flex column that needs to grow and scroll internally.
  styles: [':host { display: block; flex: 1 1 0%; min-height: 0; }'],
})
export class ConversationDetailComponent implements OnInit {
  protected readonly chatService = inject(ChatService);
  private readonly profileService = inject(ProfileService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  protected readonly conversationId = signal(0);
  protected readonly currentUserId = signal<number | null>(null);
  protected readonly infoModalOpen = signal(false);

  /** REQ-33/34 (chat-message-search PLAN.md, Amended 2026-08-10): the id/query a search-result
   * click asked us to jump to, read off `history.state` (never a query param — the SPEC's
   * amendment explicitly excludes deep-linking a message via a shareable URL). `null` on any
   * normal navigation to `/chat/:id` (typed URL, sidebar click, etc.) — this is a one-shot,
   * in-session-only signal, not part of this route's contract.
   *
   * **Deviation from the PLAN's original sketch**: `router.getCurrentNavigation()` is only
   * populated synchronously during the navigation transaction itself — reliable when a routed
   * component is created directly by a `RouterOutlet`, which is *not* this codebase's shape.
   * `ChatShellComponent` owns the `/chat/**` outlet alone and mounts this component reactively
   * off its own `route.data`/`route.queryParamMap` subscriptions (see that file's Javadoc) — by
   * the time that subscription callback fires and Angular actually constructs this component,
   * the navigation transaction has already ended and `getCurrentNavigation()` reads `null`.
   * `history.state` (what the Router's `state:` extra actually calls `pushState` with) has no
   * such timing window — confirmed via manual Playwright verification that
   * `getCurrentNavigation()`-based reads silently never fired the jump in this app's real
   * routing shape, while `history.state` did.
   *
   * **Bug fix (found live: a search-result jump only ever worked from a cold mount)**: reading
   * `history.state` only in a field initializer was wrong — `ChatShellComponent` keeps this
   * component alive across `/chat/:id` → `/chat/:otherId` navigations (its own `@if
   * (chatRouteKind() === 'peer')` branch stays true, so Angular never destroys/reconstructs this
   * component, only `route.paramMap` re-emits). A field initializer runs exactly once, at that
   * first construction, so every jump-to-message click *after* the conversation view was already
   * open silently read stale (usually absent) `history.state` and never set a jump target. Fixed
   * by re-reading `history.state` inside the same `route.paramMap.subscribe` callback that
   * updates `conversationId`, so every navigation — first mount or not — re-resolves the jump
   * request for the state that navigation actually carried. */
  private readonly jumpRequestedMessageId = signal<number | null>(null);
  protected readonly jumpToQuery = signal<string | undefined>(undefined);
  /** Set once the requested message is actually present among the loaded messages — this, not
   * `jumpRequestedMessageId`, is what `MessageThreadComponent` receives, so it only ever tries to
   * scroll to/flash a message that genuinely exists in the DOM right now. */
  protected readonly jumpTargetMessageId = signal<number | undefined>(undefined);
  private jumpLoadAttempts = 0;
  private static readonly MAX_JUMP_LOAD_ATTEMPTS = 20;
  /** Bug fix (found live: reported as an unbounded-looking request storm) — `jumpLoadAttempts`
   * used to be reset to 0 on every `route.paramMap` emission unconditionally, including a second
   * (third, ...) search-result click landing on a conversation that's already open (which,
   * unlike a real conversation switch, only refreshes `jumpRequestedMessageId`/`jumpToQuery`, not
   * `conversationId`). Since `ChatShellComponent` keeps this component mounted across
   * same-conversation re-navigations too, that let a viewer trying several different search
   * results in a row against a large, mostly-unloaded conversation re-arm a *fresh*
   * `MAX_JUMP_LOAD_ATTEMPTS`-page budget per click, on top of whatever an earlier, already-given-up
   * jump for the same conversation had already burned through — unbounded in aggregate, and the
   * one thing REQ-34's cap exists to prevent. Tracked so the reset only happens when
   * `conversationId` itself actually changes (below), giving each conversation one real
   * `MAX_JUMP_LOAD_ATTEMPTS` ceiling for as long as it stays open, no matter how many different
   * jump targets are tried against it in that time. */
  private lastOpenedConversationId: number | null = null;

  /** REQ-34: while a jump is pending and the target isn't loaded yet, keep requesting older
   * pages — Angular's own effect scheduling re-runs this on every `entryOf(...)` cache update
   * `loadOlderMessages` produces, so no manual await/poll loop is needed (this codebase's
   * existing `ChatService.patchEntry`/signal pattern already does the "notify on change" part).
   * Bounded at `MAX_JUMP_LOAD_ATTEMPTS` pages so a message that's been deleted, or sits beyond a
   * reasonable lookback, doesn't loop forever — REQ-34's "stop... with no error state". */
  private readonly jumpToMessageEffect = effect(() => {
    const targetId = this.jumpRequestedMessageId();
    if (targetId === null || this.conversationId() === 0) {
      return;
    }

    const entry = this.entry();
    const found = entry.messages.some((message) => message.id === targetId);
    if (found) {
      this.jumpTargetMessageId.set(targetId);
      this.jumpRequestedMessageId.set(null);
      this.jumpLoadAttempts = 0;
      return;
    }

    if (entry.loading) {
      return;
    }

    if (
      !entry.hasMore ||
      this.jumpLoadAttempts >= ConversationDetailComponent.MAX_JUMP_LOAD_ATTEMPTS
    ) {
      // Bug fix: NOT resetting jumpLoadAttempts here (unlike the `found` branch above) is
      // intentional — see jumpLoadAttempts's Javadoc. Giving up must permanently spend this
      // conversation's MAX_JUMP_LOAD_ATTEMPTS budget, not hand a fresh one to whatever jump
      // target comes next for the same still-open conversation.
      this.jumpRequestedMessageId.set(null);
      return;
    }

    this.jumpLoadAttempts += 1;
    this.chatService.loadOlderMessages(this.conversationId());
  });

  protected readonly detail = computed(() => this.chatService.details().get(this.conversationId()));
  protected readonly entry = computed(() => this.chatService.entryOf(this.conversationId()));
  protected readonly displayMessages = computed(() => {
    const currentUserId = this.currentUserId();
    return this.entry().messages.map((message) => ({
      ...message,
      fromViewer: message.senderUserId === currentUserId,
    }));
  });
  protected readonly viewerRelation = computed(() => {
    const detail = this.detail();
    if (!detail) {
      return undefined;
    }
    return deriveViewerRelation(detail.participantUserIds, this.currentUserId());
  });

  /** The other side of a 1:1 conversation, for `PersonInfoModalComponent`'s `userId` input —
   * `null` while `currentUserId()` hasn't resolved yet, or (defensively) if this is ever called
   * for something other than an exactly-2-participant PEER_DIRECT conversation. */
  protected readonly otherParticipantId = computed(() => {
    const detail = this.detail();
    const currentUserId = this.currentUserId();
    if (!detail || currentUserId === null) {
      return null;
    }
    return detail.participantUserIds.find((id) => id !== currentUserId) ?? null;
  });

  ngOnInit(): void {
    this.profileService
      .getOwnProfile()
      .subscribe((profile) => this.currentUserId.set(profile.userId));

    this.route.paramMap.subscribe((params) => {
      const id = Number(params.get('conversationId'));
      this.conversationId.set(id);

      // Bug fix (found live via Playwright: jumping to an already-loaded older message, after
      // jumping elsewhere and back, re-triggered an unbounded ?after=<same cursor> request storm)
      // — `openConversation` used to run unconditionally on every `paramMap` emission, including
      // re-navigations that only change the jump target within the SAME already-open conversation
      // (ChatShellComponent keeps this component mounted across those, per the class Javadoc).
      // That re-seeded `ChatService`'s message cache back down to just the latest page every
      // single time, discarding whatever older pages an in-progress or already-resolved jump had
      // paginated in — so a message that had genuinely already been loaded looked "not found"
      // again on the next jump back to it, restarting pagination from scratch, and overlapping
      // in-flight `openConversation` responses from back-to-back jumps could resolve out of order
      // and repeatedly stomp the cache back to the same boundary, looking like an infinite loop
      // requesting the same cursor. Only re-fetch/reset when this is genuinely a different
      // conversation than the one already open — a same-conversation jump-target change must
      // reuse whatever is already cached.
      const isNewConversation = id !== this.lastOpenedConversationId;
      if (isNewConversation) {
        this.chatService.openConversation(id);
      }

      const state = history.state as Record<string, unknown> | null;
      this.jumpRequestedMessageId.set((state?.['jumpToMessageId'] as number | undefined) ?? null);
      this.jumpToQuery.set(state?.['jumpToQuery'] as string | undefined);
      this.jumpTargetMessageId.set(undefined);
      // Only re-arm the per-conversation MAX_JUMP_LOAD_ATTEMPTS budget when this is genuinely a
      // different conversation than the one already open — see jumpLoadAttempts's Javadoc.
      if (isNewConversation) {
        this.jumpLoadAttempts = 0;
      }
      this.lastOpenedConversationId = id;
    });

    interval(POLL_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.document.visibilityState === 'visible') {
          this.chatService.pollNewMessages(this.conversationId());
        }
      });
  }

  onSend(content: string): void {
    this.chatService.sendMessage(this.conversationId(), content, crypto.randomUUID()).subscribe();
  }

  onRetry(message: { localId?: string; content: string }): void {
    if (!message.localId) {
      return;
    }
    this.chatService
      .sendMessage(this.conversationId(), message.content, message.localId)
      .subscribe();
  }
}
