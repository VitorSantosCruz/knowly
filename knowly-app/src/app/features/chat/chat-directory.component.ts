import { Component, OnInit, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  ChatDirectoryRowsService,
  DirectoryRow,
  GroupRow,
  PersonRow,
} from '../../core/chat-directory-rows.service';
import { AvatarComponent } from '../../shared/avatar.component';
import { GroupVisibilityBadgeComponent } from './group-visibility-badge.component';

/**
 * Directory column's (column 1 of the 3-column layout — REQ-1/REQ-2, Amended (3), final)
 * permanent content — one unified "CONVERSAS" list: the always-pinned Support row first, then
 * every person already messaged, every group already joined, and every existing "Base de
 * artigos" conversation, mixed together (not partitioned into sections).
 *
 * **Amended (2026-08-10)**: this column's own `unifiedQuery` search field is removed —
 * `rowsService.conversationRows()` renders directly, unfiltered. Finding anything by name now
 * happens exclusively through the persistent search bar (`chat-unified-search.component.ts`,
 * mounted by `ChatShellComponent`'s own header region) — see that feature's PLAN.md. This
 * column's browsing/click-to-open-or-create/join/request-to-join logic and Support's pinned-
 * first ordering are entirely unchanged by this amendment.
 *
 * **Amendment (3), 2026-08-09 (same day as the previous 2-column cut this supersedes)**: the
 * product owner's earlier "já falou"/"ainda não falou" partition (People) and the separate
 * Groups section are both retired — not-yet-messaged people and discoverable, non-member groups
 * move out entirely to `chat-full-directory.component.ts` (column 3), so this column only ever
 * shows conversations the viewer already has. See `ChatDirectoryRowsService.conversationRows`'s
 * own doc comment for the merge/sort rationale.
 *
 * The actual click-to-open-or-create/join/request-to-join logic and the underlying data
 * (`ChatService`/`ChatDirectoryService`/`ChatGroupService`) live in `ChatDirectoryRowsService`,
 * shared with column 3.
 */
@Component({
  selector: 'app-chat-directory',
  imports: [TranslocoPipe, GroupVisibilityBadgeComponent, AvatarComponent],
  template: `
    <div data-testid="chat-directory" class="flex flex-col gap-4">
      <ul data-testid="chat-directory-list" class="flex flex-col gap-1">
        @for (row of rowsService.conversationRows(); track row.key) {
          <li>
            @switch (row.kind) {
              @case ('support') {
                <button
                  type="button"
                  data-testid="chat-directory-row-support"
                  [attr.aria-label]="'chat.directory.supportRowAriaLabel' | transloco"
                  (click)="rowsService.onSupportClick()"
                  [attr.aria-current]="isSupportActive() ? 'page' : null"
                  class="flex w-full items-center justify-between rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
                  [class.bg-signal-50]="isSupportActive()"
                  [class.dark:bg-signal-900]="isSupportActive()"
                >
                  {{ 'chat.directory.supportRowLabel' | transloco }}
                </button>
              }
              @case ('person') {
                <button
                  type="button"
                  [attr.data-testid]="'chat-directory-row-' + row.key"
                  [attr.aria-label]="
                    'chat.directory.personRowAriaLabel'
                      | transloco: { nickname: personRow(row).displayName }
                  "
                  (click)="onPersonClick(personRow(row))"
                  [attr.aria-current]="isPersonRowActive(personRow(row)) ? 'page' : null"
                  class="flex w-full items-center gap-2 rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
                  [class.bg-signal-50]="isPersonRowActive(personRow(row))"
                  [class.dark:bg-signal-900]="isPersonRowActive(personRow(row))"
                >
                  <app-avatar [avatarUrl]="personRow(row).avatarUrl" />
                  <span>{{ personRow(row).displayName }}</span>
                </button>
                @if (rowErrors().has(row.key)) {
                  <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                    {{ 'chat.directory.actionError' | transloco }}
                  </p>
                }
              }
              @case ('group') {
                <button
                  type="button"
                  [attr.data-testid]="'chat-directory-row-' + row.key"
                  [attr.aria-label]="
                    'chat.directory.groupRowAriaLabel'
                      | transloco: { title: groupRow(row).displayName }
                  "
                  (click)="onGroupClick(groupRow(row))"
                  [attr.aria-current]="groupRow(row).id === activePeerId() ? 'page' : null"
                  class="flex w-full items-center justify-between rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
                  [class.bg-signal-50]="groupRow(row).id === activePeerId()"
                  [class.dark:bg-signal-900]="groupRow(row).id === activePeerId()"
                >
                  <span class="flex items-center gap-2">
                    <app-avatar kind="group" [icon]="groupRow(row).icon ?? null" />
                    {{ groupRow(row).displayName }}
                  </span>
                  @if (groupRow(row).visibility) {
                    <app-group-visibility-badge [visibility]="groupRow(row).visibility!" />
                  }
                </button>
                @if (rowErrors().has(row.key)) {
                  <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                    {{ 'chat.directory.actionError' | transloco }}
                  </p>
                }
              }
              @case ('article') {
                <button
                  type="button"
                  [attr.data-testid]="'chat-directory-row-' + row.key"
                  [attr.aria-label]="
                    'chat.directory.articleRowAriaLabel'
                      | transloco
                        : {
                            title:
                              articleRow(row).displayName ||
                              ('chat.directory.untitledArticleConversation' | transloco),
                          }
                  "
                  (click)="rowsService.onArticleClick(articleRow(row))"
                  [attr.aria-current]="articleRow(row).id === activeArticleId() ? 'page' : null"
                  class="flex w-full items-center gap-2 rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
                  [class.bg-signal-50]="articleRow(row).id === activeArticleId()"
                  [class.dark:bg-signal-900]="articleRow(row).id === activeArticleId()"
                >
                  <app-avatar kind="group" [icon]="articleRow(row).icon" />
                  {{
                    articleRow(row).displayName ||
                      ('chat.directory.untitledArticleConversation' | transloco)
                  }}
                </button>
              }
            }
          </li>
        }
      </ul>
    </div>
  `,
})
export class ChatDirectoryComponent implements OnInit {
  protected readonly rowsService = inject(ChatDirectoryRowsService);
  private readonly router = inject(Router);

  /** UX fix (2026-08-09): the currently-open conversation/group/article/Support row gets a
   * visual "active" state (mirrors `nav-menu.component.ts`'s active-nav-link convention),
   * derived purely from the current URL — never from anything that changes on click alone, so
   * this can't itself cause the reordering flicker a tester reported (see
   * `ChatDirectoryRowsService`'s own doc comment on why sort order never reacts to selection). */
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  protected readonly activePeerId = computed(() => {
    const match = /^\/chat\/(\d+)(?:\?|$)/.exec(this.currentUrl());
    return match ? Number(match[1]) : null;
  });

  protected readonly activeArticleId = computed(() => {
    const match = /^\/chat\/articles\/(\d+)/.exec(this.currentUrl());
    return match ? Number(match[1]) : null;
  });

  protected isSupportActive(): boolean {
    const url = this.currentUrl();
    return url.startsWith('/chat/support') || url.includes('section=support');
  }

  protected isPersonRowActive(row: PersonRow): boolean {
    return row.conversationId !== null && row.conversationId === this.activePeerId();
  }

  protected readonly rowErrors = this.rowsService.rowErrors;

  ngOnInit(): void {
    this.rowsService.ensureLoaded();
  }

  protected personRow(row: DirectoryRow): PersonRow {
    return row as PersonRow;
  }

  protected groupRow(row: DirectoryRow): GroupRow {
    return row as GroupRow;
  }

  protected articleRow(row: DirectoryRow) {
    return row as Extract<DirectoryRow, { kind: 'article' }>;
  }

  protected onPersonClick(row: PersonRow): void {
    this.rowsService.onPersonClick(row);
  }

  protected onGroupClick(row: GroupRow): void {
    this.rowsService.onGroupClick(row);
  }
}
