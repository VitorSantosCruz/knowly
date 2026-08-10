import { Component, OnInit, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  ChatDirectoryRowsService,
  GroupRow,
  PersonRow,
} from '../../core/chat-directory-rows.service';
import { AvatarComponent } from '../../shared/avatar.component';
import { GroupVisibilityBadgeComponent } from './group-visibility-badge.component';

/**
 * Full-directory column (column 3 of the 3-column layout — REQ-2d, Amended (3), final), same
 * width as column 1: everyone/every group NOT already in column 1's unified list —
 * not-yet-messaged people and discoverable groups the viewer isn't a participant of, per
 * `ChatDirectoryRowsService.discoveryRows()`'s disjoint-complement rule.
 *
 * **Amended (2026-08-10)**: this column's own `searchQuery` field is removed —
 * `rowsService.discoveryRows()` renders directly, unfiltered. Finding anything by name now
 * happens exclusively through the persistent search bar. This column's sort order and
 * click-to-open-or-create/join/request-to-join logic are entirely unchanged by this amendment.
 *
 * Why a separate component rather than extending `ChatDirectoryComponent`: column 1 and column
 * 3 render disjoint row sets with different empty-state copy, different a11y labels, and (per
 * REQ-2d) a materially different sort — see `PLAN.md`'s "New third-column component" section.
 * Reuses `ChatDirectoryRowsService`'s existing click-to-open-or-create/join/request-to-join
 * handlers as-is (REQ-3's "applies identically regardless of whether the row is in column 1 or
 * column 3") — no new interaction logic here, only presentation.
 *
 * **Sort — interim fallback, not yet the real REQ-2d ranking:** see
 * `ChatDirectoryRowsService.discoveryRows()`'s own doc comment (alphabetical by `displayName`,
 * pending a new backend cross-surface-recency endpoint).
 */
@Component({
  selector: 'app-chat-full-directory',
  imports: [TranslocoPipe, GroupVisibilityBadgeComponent, AvatarComponent],
  template: `
    <div data-testid="chat-full-directory" class="flex flex-col gap-4">
      @if (rowsService.discoveryRows().length === 0) {
        <p data-testid="chat-full-directory-empty" class="text-sm text-ink-500 dark:text-ink-400">
          {{ 'chat.fullDirectory.emptyState' | transloco }}
        </p>
      } @else {
        <ul data-testid="chat-full-directory-list" class="flex flex-col gap-1">
          @for (row of rowsService.discoveryRows(); track row.key) {
            <li>
              @if (row.kind === 'person') {
                <button
                  type="button"
                  [attr.data-testid]="'chat-full-directory-row-' + row.key"
                  [attr.aria-label]="
                    'chat.directory.personRowAriaLabel' | transloco: { nickname: row.displayName }
                  "
                  (click)="onPersonClick(row)"
                  class="flex w-full items-center gap-2 rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
                >
                  <app-avatar [avatarUrl]="row.avatarUrl" />
                  <span>{{ row.displayName }}</span>
                </button>
                @if (rowErrors().has(row.key)) {
                  <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                    {{ 'chat.directory.actionError' | transloco }}
                  </p>
                }
              } @else {
                <button
                  type="button"
                  [attr.data-testid]="'chat-full-directory-row-' + row.key"
                  [attr.aria-label]="groupRowAriaLabel(row) | transloco: { title: row.displayName }"
                  [disabled]="pendingGroupIds().has(row.id)"
                  (click)="onGroupClick(row)"
                  class="flex w-full items-center justify-between rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 disabled:opacity-60 dark:border-ink-800/70 dark:hover:bg-ink-800"
                >
                  <span class="flex items-center gap-2">
                    <app-avatar kind="group" />
                    {{ row.displayName }}
                  </span>
                  @if (row.visibility) {
                    <app-group-visibility-badge [visibility]="row.visibility" />
                  }
                </button>
                @if (pendingGroupIds().has(row.id)) {
                  <p
                    data-testid="chat-full-directory-request-pending"
                    class="mt-1 text-xs text-ink-500 dark:text-ink-400"
                  >
                    {{ 'chat.directory.requestPending' | transloco }}
                  </p>
                }
                @if (rowErrors().has(row.key)) {
                  <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                    {{ 'chat.directory.actionError' | transloco }}
                  </p>
                }
              }
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class ChatFullDirectoryComponent implements OnInit {
  protected readonly rowsService = inject(ChatDirectoryRowsService);

  protected readonly rowErrors = this.rowsService.rowErrors;
  protected readonly pendingGroupIds = this.rowsService.pendingGroupIds;

  ngOnInit(): void {
    this.rowsService.ensureLoaded();
  }

  protected groupRowAriaLabel(row: GroupRow): string {
    return row.visibility === 'REQUEST_TO_JOIN'
      ? 'chat.directory.requestToJoinAriaLabel'
      : 'chat.directory.joinGroupAriaLabel';
  }

  protected onPersonClick(row: PersonRow): void {
    this.rowsService.onPersonClick(row);
  }

  protected onGroupClick(row: GroupRow): void {
    this.rowsService.onGroupClick(row);
  }
}
