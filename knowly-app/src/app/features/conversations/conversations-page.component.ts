import { Component, OnInit, effect, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EMPTY, catchError, of } from 'rxjs';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { ConversationService, ConversationSummary, Message } from '../../core/conversation.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';

type ConversationsError = 'network' | 'permission-denied' | null;

let nextLocalMessageId = -1;

@Component({
  selector: 'app-conversations-page',
  imports: [TranslocoPipe, ErrorStateComponent, NoAccessStateComponent],
  template: `
    <div data-testid="conversations-page" class="flex gap-6 p-6">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-slate-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <aside class="w-64 shrink-0">
          <button
            data-testid="new-conversation"
            (click)="onNewConversation()"
            class="mb-3 w-full rounded bg-indigo-600 px-3 py-1.5 text-sm text-white"
          >
            {{ 'conversations.new' | transloco }}
          </button>
          <ul data-testid="conversation-list">
            @for (conversation of conversations(); track conversation.id) {
              <li>
                <button
                  [attr.data-testid]="'select-conversation-' + conversation.id"
                  (click)="onSelectConversation(conversation.id)"
                  class="w-full truncate rounded px-2 py-1.5 text-left text-sm hover:bg-slate-200 dark:hover:bg-slate-800"
                >
                  {{ conversation.title ?? ('conversations.untitled' | transloco) }}
                </button>
              </li>
            }
          </ul>
        </aside>

        <section class="flex-1">
          <ul data-testid="transcript" class="mb-4 flex flex-col gap-2">
            @for (message of messages(); track message.id) {
              <li
                [attr.data-testid]="'message-role-' + message.role"
                [class]="
                  message.role === 'USER'
                    ? 'self-end rounded-lg bg-indigo-600 px-3 py-2 text-sm text-white'
                    : 'self-start rounded-lg bg-slate-200 px-3 py-2 text-sm text-slate-900 dark:bg-slate-800 dark:text-slate-100'
                "
              >
                {{ message.content }}
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
              class="flex-1 rounded border border-slate-300 px-2 py-1.5"
            />
            <button
              type="submit"
              [disabled]="sending() || activeConversationId() === null"
              class="rounded bg-indigo-600 px-3 py-1.5 text-sm text-white disabled:opacity-50"
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
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly conversationService = inject(ConversationService);

  protected readonly conversations = signal<ConversationSummary[]>([]);
  protected readonly activeConversationId = signal<number | null>(null);
  protected readonly messages = signal<Message[]>([]);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);
  protected readonly streamError = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<ConversationsError>(null);

  private hasLoaded = false;

  constructor() {
    effect(() => {
      const tenantId = this.activeTenantService.activeTenantId();

      if (tenantId !== null && !this.hasLoaded) {
        this.hasLoaded = true;
        this.loadConversations(tenantId);
      }
    });
  }

  ngOnInit(): void {
    this.activeTenantService.fetch();
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

  protected onNewConversation(): void {
    const tenantId = this.activeTenantService.activeTenantId();

    if (tenantId === null) {
      return;
    }

    this.conversationService
      .create(tenantId)
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
