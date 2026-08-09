import { Component, OnInit, effect, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LucideLibrary, LucidePencil } from '@lucide/angular';
import { EMPTY, catchError, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { ConversationService, ConversationSummary, Message } from '../../core/conversation.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { NoActiveTenantStateComponent } from '../../shared/no-active-tenant-state.component';
import { RenameFormComponent } from '../../shared/chat/rename-form.component';
import { ChatIconComponent } from '../../shared/chat/chat-icon.component';

type ConversationsError = 'network' | 'permission-denied' | null;

let nextLocalMessageId = -1;

@Component({
  selector: 'app-conversations-page',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    NoActiveTenantStateComponent,
    LucideLibrary,
    LucidePencil,
    RenameFormComponent,
    ChatIconComponent,
  ],
  template: `
    <div data-testid="conversations-page" class="page-shell flex gap-6">
      @if (!activeTenantService.activeTenantResolved()) {
        <p data-testid="loading-state" class="text-sm text-ink-500 dark:text-ink-400">…</p>
      } @else if (activeTenantService.activeTenantId() === null) {
        <app-no-active-tenant-state />
      } @else if (loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-500 dark:text-ink-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        @if (!openedFromRoute()) {
          <aside class="w-64 shrink-0">
            <button
              data-testid="new-conversation"
              (click)="onNewConversation()"
              [class]="newConversationButtonClass + ' mb-3 w-full'"
            >
              {{ 'conversations.new' | transloco }}
            </button>
            <ul
              data-testid="conversation-list"
              role="listbox"
              class="flex w-full flex-col gap-1 border-0"
            >
              @for (conversation of conversations(); track conversation.id) {
                <li role="option" [attr.aria-selected]="conversation.id === activeConversationId()">
                  <button
                    type="button"
                    [attr.data-testid]="'select-conversation-' + conversation.id"
                    class="block w-full truncate rounded-lg px-2 py-1.5 text-left text-sm text-ink-700 transition-colors duration-fast ease-fluid hover:bg-ink-100 dark:text-ink-300 dark:hover:bg-ink-800"
                    (click)="onSelectConversation(conversation.id)"
                  >
                    {{ conversation.title ?? ('conversations.untitled' | transloco) }}
                  </button>
                </li>
              }
            </ul>
          </aside>
        }

        <section class="flex flex-1 flex-col">
          @if (renaming()) {
            <app-rename-form
              [initialTitle]="activeConversationTitle() ?? ''"
              [initialIcon]="activeConversationIcon()"
              [error]="renameError()"
              (saved)="onRenameSaved($event)"
              (cancelled)="renaming.set(false)"
            />
          } @else {
            <header data-testid="conversations-header" class="mb-3 flex items-center gap-2">
              <!-- REQ: knowledge-base (RAG) conversations are represented by a knowledge-base
                   icon, not a person's photo. Amendment (4): once a conversation is open, its
                   own chosen icon/title (already computed above for the rename form) render
                   here too, so a named/iconed conversation doesn't look identical to an unnamed
                   one once opened; the generic label/icon remain the empty-state default. -->
              <div
                class="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-ink-100 p-2 text-ink-500 dark:bg-ink-800 dark:text-ink-400"
              >
                @if (activeConversationIcon(); as icon) {
                  <app-chat-icon [icon]="icon" data-testid="conversations-header-icon" />
                } @else {
                  <svg
                    lucideLibrary
                    data-testid="conversations-header-icon"
                    aria-hidden="true"
                  ></svg>
                }
              </div>
              <h1 class="font-semibold text-ink-900 dark:text-white">
                {{
                  activeConversationId() !== null
                    ? (activeConversationTitle() ?? ('conversations.untitled' | transloco))
                    : ('conversations.title' | transloco)
                }}
              </h1>
              <!-- Amendment (4), REQ-39: this list is already owner-scoped by construction
                   (GET /api/tenants/tenantId/conversations only ever returns the caller's own
                   RAG conversations), so "only the conversation's own owning participant may
                   rename it" is satisfied by the mere presence of an open conversation here —
                   no separate ownership computed is needed/available on the wire today. -->
              @if (activeConversationId() !== null) {
                <button
                  type="button"
                  data-testid="conversations-header-rename"
                  [attr.aria-label]="
                    'chat.rename.pencilAriaLabel'
                      | transloco: { title: activeConversationTitle() ?? '' }
                  "
                  (click)="renaming.set(true)"
                  class="rounded-lg p-1 text-ink-500 hover:bg-ink-100 dark:text-ink-400 dark:hover:bg-ink-800"
                >
                  <svg lucidePencil class="h-4 w-4" aria-hidden="true"></svg>
                </button>
              }
            </header>
          }

          <ul
            data-testid="transcript"
            class="mb-4 flex flex-1 flex-col gap-2 rounded-2xl border border-ink-200/70 bg-white p-4 shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
          >
            @for (message of messages(); track message.id) {
              <li
                [attr.data-testid]="'message-role-' + message.role"
                class="enter-fluid"
                [class]="
                  message.role === 'USER'
                    ? 'self-end rounded-2xl rounded-br-sm bg-ink-800 px-3 py-2 text-sm text-white shadow-sm dark:bg-ink-700'
                    : 'self-start rounded-2xl rounded-bl-sm bg-ink-100 px-3 py-2 text-sm text-ink-900 shadow-sm dark:bg-ink-800 dark:text-ink-100'
                "
              >
                @if (message.role === 'ASSISTANT' && sending() && message.content === '') {
                  <span
                    data-testid="assistant-typing-indicator"
                    class="inline-flex items-center gap-1"
                    aria-live="polite"
                  >
                    <span
                      class="h-1.5 w-1.5 animate-bounce rounded-full bg-signal-500 [animation-delay:-0.2s]"
                    ></span>
                    <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-signal-500"></span>
                    <span
                      class="h-1.5 w-1.5 animate-bounce rounded-full bg-signal-500 [animation-delay:0.2s]"
                    ></span>
                  </span>
                } @else {
                  {{ message.content }}
                }
              </li>
            }
          </ul>

          @if (streamError(); as streamErrorMessage) {
            <p
              data-testid="message-stream-error"
              class="mb-2 text-sm text-red-700 dark:text-red-300"
            >
              {{ streamErrorMessage }}
            </p>
          }

          <form data-testid="send-message-form" class="flex gap-2" (submit)="onSend($event)">
            <input
              data-testid="message-input"
              type="text"
              [value]="draft()"
              (input)="draft.set($any($event.target).value)"
              [disabled]="sending() || activeConversationId() === null"
              class="flex-1 rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white"
            />
            <button
              type="submit"
              [class]="sendButtonClass"
              [disabled]="sending() || activeConversationId() === null"
            >
              {{ 'conversations.send' | transloco }}
            </button>
          </form>
        </section>
      }
    </div>
  `,
})
export class ConversationsPageComponent implements OnInit {
  protected readonly activeTenantService = inject(ActiveTenantService);
  private readonly conversationService = inject(ConversationService);
  private readonly translocoService = inject(TranslocoService);
  private readonly route = inject(ActivatedRoute);

  protected readonly newConversationButtonClass = buttonClass('primary');
  protected readonly sendButtonClass = buttonClass('primary');
  protected readonly conversations = signal<ConversationSummary[]>([]);
  protected readonly activeConversationId = signal<number | null>(null);
  protected readonly messages = signal<Message[]>([]);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);
  protected readonly streamError = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<ConversationsError>(null);

  protected readonly renaming = signal(false);
  protected readonly renameError = signal(false);

  /** Amendment: true when this page was opened at `/chat/articles/:conversationId` (a sidebar
   * click on a specific knowledge-base conversation) rather than the bare browsing state — hides
   * the in-page list so the layout matches `ConversationDetailComponent`'s single-conversation
   * view for peer/group chats. */
  protected readonly openedFromRoute = signal(false);

  protected readonly activeConversationTitle = () =>
    this.conversations().find((c) => c.id === this.activeConversationId())?.title ?? null;
  protected readonly activeConversationIcon = () =>
    this.conversations().find((c) => c.id === this.activeConversationId())?.icon ?? null;

  private hasLoaded = false;
  private pendingConversationId: number | null = null;

  constructor() {
    effect(() => {
      const tenantId = this.activeTenantService.activeTenantId();

      if (tenantId !== null && !this.hasLoaded) {
        this.hasLoaded = true;
        this.loadConversations(tenantId);

        if (this.pendingConversationId !== null) {
          this.onSelectConversation(this.pendingConversationId);
          this.pendingConversationId = null;
        }
      }
    });
  }

  ngOnInit(): void {
    this.activeTenantService.fetch();

    // Amendment: opening a specific knowledge-base conversation from the sidebar navigates to
    // `/chat/articles/:conversationId` — read it here so this page opens that conversation
    // directly instead of always landing on the bare list (matching `ConversationDetailComponent`'s
    // peer/group behavior).
    this.route.paramMap.subscribe((params) => {
      const idParam = params.get('conversationId');
      this.openedFromRoute.set(idParam !== null);
      if (idParam === null) {
        return;
      }

      const conversationId = Number(idParam);
      if (this.activeTenantService.activeTenantId() !== null) {
        this.onSelectConversation(conversationId);
      } else {
        this.pendingConversationId = conversationId;
      }
    });
  }

  private loadConversations(tenantId: number): void {
    this.loading.set(true);
    this.error.set(null);

    this.conversationService
      .list(tenantId)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of<ConversationSummary[]>([]);
        }),
      )
      .subscribe((conversations) => {
        this.conversations.set(conversations);
        this.loading.set(false);
      });
  }

  /** Amendment (4), REQ-39: on success, patches this component's own `conversations()` list (the
   * same signal column 1's `ChatDirectoryRowsService.articleRows` re-derives its own rows
   * from — see that service's `maybeFetchArticles()`) so the row reflects the new name/icon
   * without a full page reload; on failure, one shared, status-code-agnostic error string (per
   * AppSec's requirement — the backend's `404` for "not your conversation" must render exactly
   * like a `400`/network failure, never a more specific "not found" string). */
  protected onRenameSaved(event: { title: string; icon: ConversationSummary['icon'] }): void {
    const tenantId = this.activeTenantService.activeTenantId();
    const conversationId = this.activeConversationId();
    if (tenantId === null || conversationId === null) {
      return;
    }
    this.renameError.set(false);
    this.conversationService
      .rename(tenantId, conversationId, event.title, event.icon ?? undefined)
      .subscribe({
        next: (updated) => {
          this.conversations.update((list) =>
            list.map((c) => (c.id === conversationId ? updated : c)),
          );
          this.renaming.set(false);
        },
        error: () => this.renameError.set(true),
      });
  }

  protected onNewConversation(): void {
    const tenantId = this.activeTenantService.activeTenantId();

    if (tenantId === null) {
      return;
    }

    // Amendment (4), REQ-38: naming now happens via `create-conversation-dialog.component.ts`,
    // reached from the sidebar's "Falar com a base de artigos" action — this in-page "+ Nova
    // conversa" button (a secondary, pre-existing creation path inside an already-open RAG view,
    // out of REQ-38's own scope) keeps working by falling back to the same untitled default
    // string `conversations.untitled` already renders for a `null` title, satisfying the
    // backend's now-required non-blank `title`.
    this.conversationService
      .create(tenantId, this.translocoService.translate('conversations.new'))
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return EMPTY;
        }),
      )
      .subscribe((conversation) => {
        this.conversations.update((conversations) => [conversation, ...conversations]);
        this.activeConversationId.set(conversation.id);
        this.messages.set([]);
        this.streamError.set(null);
      });
  }

  protected onSelectConversation(conversationId: number): void {
    const tenantId = this.activeTenantService.activeTenantId();

    if (tenantId === null) {
      return;
    }

    this.conversationService
      .getDetail(tenantId, conversationId)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return EMPTY;
        }),
      )
      .subscribe((detail) => {
        this.activeConversationId.set(detail.id);
        this.messages.set(detail.messages);
        this.streamError.set(null);
      });
  }

  protected onSend(event: Event): void {
    event.preventDefault();
    const tenantId = this.activeTenantService.activeTenantId();
    const conversationId = this.activeConversationId();
    const content = this.draft();

    if (tenantId === null || conversationId === null || !content || this.sending()) {
      return;
    }

    this.draft.set('');
    this.streamError.set(null);
    this.messages.update((messages) => [
      ...messages,
      { id: nextLocalMessageId--, role: 'USER', content },
    ]);
    const assistantMessageId = nextLocalMessageId--;
    this.messages.update((messages) => [
      ...messages,
      { id: assistantMessageId, role: 'ASSISTANT', content: '' },
    ]);
    this.sending.set(true);

    this.conversationService.sendMessage(tenantId, conversationId, content).subscribe({
      next: (chatEvent) => {
        if (chatEvent.type === 'message') {
          this.appendToAssistantMessage(assistantMessageId, chatEvent.data);
        } else if (chatEvent.type === 'done') {
          this.sending.set(false);
        } else if (chatEvent.type === 'error') {
          this.streamError.set(chatEvent.data);
          this.sending.set(false);
        } else if (chatEvent.type === 'permission-denied') {
          this.sending.set(false);
          this.error.set('permission-denied');
        }
      },
      error: () => {
        this.streamError.set('The assistant is unavailable.');
        this.sending.set(false);
      },
    });
  }

  private appendToAssistantMessage(messageId: number, delta: string): void {
    this.messages.update((messages) =>
      messages.map((message) =>
        message.id === messageId ? { ...message, content: message.content + delta } : message,
      ),
    );
  }
}
