import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConversationListComponent } from './conversation-list.component';

/**
 * Route: `/chat` (no guard, per PLAN.md's rationale — peer messaging is available to any
 * authenticated user regardless of role, and `STAFF_ADMIN` oversight spans every tenant).
 * The list is always rendered; the detail/new-conversation views render into the child outlet.
 */
@Component({
  selector: 'app-chat-page',
  imports: [ConversationListComponent, RouterOutlet],
  template: `
    <div data-testid="chat-page" class="page-shell grid gap-6 md:grid-cols-[320px_1fr]">
      <app-conversation-list />
      <div
        class="rounded-2xl border border-ink-200/70 bg-white p-4 dark:border-ink-800/70 dark:bg-ink-900"
      >
        <router-outlet />
      </div>
    </div>
  `,
})
export class ChatPageComponent {}
