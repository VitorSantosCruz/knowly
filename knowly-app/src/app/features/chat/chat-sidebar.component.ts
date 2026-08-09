import { Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucidePlus } from '@lucide/angular';

/**
 * Directory column's (column 1) header — 3 direct action buttons, never a tab strip
 * (REQ-1/REQ-2, amended 2026-08-09: the shipped tab-strip design was reported unintuitive by
 * the product owner and replaced by a persistent 3-column, messaging-app-style layout).
 *
 * This component used to own the People/Groups/Support/Base-de-artigos tab switch
 * (`ChatSection`/`sectionChange`) — that responsibility is retired. `ChatDirectoryComponent`
 * (the unified, always-visible, searchable list) is the directory column's permanent content;
 * this header only ever exposes the 3 actions REQ-2 calls out: open a support ticket, start a
 * new "Base de artigos" conversation, and create a group. `ChatShellComponent` owns what each
 * action actually does (navigate/create/open a dialog).
 *
 * **Bug fix (2026-08-09, reported by a tester on the shipped 3-column cut)**: "Abrir chamado de
 * suporte" (opening a brand-new ticket, a member action inside a tenant) and "Falar com a base
 * de artigos" (creating a brand-new RAG conversation, which the backend endpoint itself scopes
 * to a tenant) both require an active tenant to mean anything — same reasoning
 * `ConversationsPageComponent`'s existing "no active tenant selected" empty state already
 * encodes for the RAG section. A staff viewer with no active tenant (pure cross-tenant
 * oversight) must not see these two as available actions. "Criar grupo" stays unconditional —
 * a staff-only group is a valid concept independent of any tenant, per
 * `chat-group-membership-management`. This does **not** hide the directory list's own always-
 * present Support row (REQ-2's "existing Support channel/ticket... or staff unclaimed-inbox"
 * reachability) — only this *new-ticket* action, which is genuinely tenant-scoped.
 *
 * **UX fix (2026-08-09)**: "Criar grupo" originally used the same border-only, unfilled style
 * as the other 2 actions, which a tester read as a list item stuck in a permanently-"selected"
 * state (list rows have no selection styling of their own) rather than a clear button. It
 * already used this app's filled CTA style (`bg-signal-600`/`hover:bg-signal-700`) — the fix
 * adds a `LucidePlus` icon alongside the label so it reads unambiguously as an action, not a
 * list row.
 */
@Component({
  selector: 'app-chat-sidebar',
  imports: [TranslocoPipe, LucidePlus],
  template: `
    <div data-testid="chat-sidebar" class="flex flex-col gap-2">
      @if (hasActiveTenant()) {
        <button
          type="button"
          data-testid="chat-sidebar-action-support"
          [attr.aria-label]="'chat.sidebar.actionSupportAriaLabel' | transloco"
          (click)="openSupport.emit()"
          class="rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm font-medium hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
        >
          {{ 'chat.sidebar.actionSupport' | transloco }}
        </button>

        <button
          type="button"
          data-testid="chat-sidebar-action-articles"
          [attr.aria-label]="'chat.sidebar.actionArticlesAriaLabel' | transloco"
          (click)="openArticles.emit()"
          class="rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm font-medium hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
        >
          {{ 'chat.sidebar.actionArticles' | transloco }}
        </button>
      }

      <button
        type="button"
        data-testid="chat-sidebar-action-create-group"
        [attr.aria-label]="'chat.directory.createGroupAriaLabel' | transloco"
        (click)="createGroup.emit()"
        class="flex items-center gap-2 rounded-lg bg-signal-600 px-3 py-2 text-left text-sm font-medium text-white hover:bg-signal-700"
      >
        <svg lucidePlus class="h-4 w-4 shrink-0" aria-hidden="true"></svg>
        {{ 'chat.directory.createGroup' | transloco }}
      </button>
    </div>
  `,
})
export class ChatSidebarComponent {
  readonly hasActiveTenant = input<boolean>(false);
  readonly openSupport = output<void>();
  readonly openArticles = output<void>();
  readonly createGroup = output<void>();
}
