import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChatGroupService } from '../../core/chat-group.service';
import { ChatGroupVisibility, ConversationDetail } from '../../core/chat.model';
import { GroupVisibilityBadgeComponent } from './group-visibility-badge.component';

const VISIBILITY_VALUES: ChatGroupVisibility[] = ['PRIVATE', 'REQUEST_TO_JOIN', 'PUBLIC'];

/**
 * REQ-14/15/22/23/24/28/29/30/31/32 — pending join requests + visibility change +
 * promote-to-admin + remove-participant + delete-group, rendered inside a group's own view.
 * Every action is gated on `isAdmin` (derived client-side from `adminUserIds`, never a
 * separate capability fetch, per PLAN.md) — a non-admin viewer gets none of this rendered at
 * all (removed from the DOM entirely, not merely hidden — appsec requirement).
 *
 * Deviation from PLAN.md's "reuses ConfirmDialogComponent" note: that component's actual shape
 * requires a backend-issued typed confirmation *word* (`fetchToken`), a flow designed for
 * higher-risk deletions (see `deletion-confirmation-token`) with no equivalent endpoint in this
 * feature's API contract. Reusing it here would require inventing a token endpoint out of
 * scope for this SPEC. Instead this panel uses a lightweight inline "are you sure?" confirm
 * step (click again to confirm) for remove-participant/delete-group, consistent in spirit
 * (an explicit second step before an irreversible action) without requiring that endpoint.
 */
@Component({
  selector: 'app-group-admin-panel',
  imports: [TranslocoPipe, GroupVisibilityBadgeComponent],
  template: `
    @if (isAdmin()) {
      <section data-testid="group-admin-panel" class="flex flex-col gap-4">
        <h2 class="text-sm font-semibold text-ink-900 dark:text-white">
          {{ 'chat.adminPanel.title' | transloco }}
        </h2>

        @if (pendingRequests().length > 0) {
          <div>
            <h3 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
              {{ 'chat.adminPanel.pendingRequestsTitle' | transloco }}
            </h3>
            <ul class="flex flex-col gap-2">
              @for (request of pendingRequests(); track request.id) {
                <li
                  class="flex flex-col gap-1 rounded-lg border border-ink-200/70 px-3 py-2 text-sm dark:border-ink-800/70"
                >
                  <div class="flex items-center justify-between gap-2">
                    <span>{{ request.requesterNickname }}</span>
                    <div class="flex gap-2">
                      <button
                        type="button"
                        [attr.data-testid]="'approve-request-' + request.id"
                        [attr.aria-label]="
                          'chat.adminPanel.approveAriaLabel'
                            | transloco: { nickname: request.requesterNickname }
                        "
                        (click)="approve(request.id)"
                        class="rounded-lg bg-signal-600 px-2 py-1 text-xs font-medium text-white"
                      >
                        {{ 'chat.adminPanel.approve' | transloco }}
                      </button>
                      <button
                        type="button"
                        [attr.data-testid]="'reject-request-' + request.id"
                        [attr.aria-label]="
                          'chat.adminPanel.rejectAriaLabel'
                            | transloco: { nickname: request.requesterNickname }
                        "
                        (click)="reject(request.id)"
                        class="rounded-lg px-2 py-1 text-xs font-medium text-ink-700 dark:text-ink-200"
                      >
                        {{ 'chat.adminPanel.reject' | transloco }}
                      </button>
                    </div>
                  </div>
                  @if (requestErrors().get(request.id); as message) {
                    <p role="alert" class="text-xs text-red-600 dark:text-red-400">{{ message }}</p>
                  }
                </li>
              }
            </ul>
          </div>
        }

        <div>
          <h3 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
            {{ 'chat.adminPanel.changeVisibility' | transloco }}
          </h3>
          <div class="flex items-center gap-2">
            <app-group-visibility-badge [visibility]="detail().visibility" />
            <select
              data-testid="group-admin-visibility-select"
              [attr.aria-label]="'chat.adminPanel.changeVisibility' | transloco"
              [value]="detail().visibility"
              (change)="onVisibilityChange($any($event.target).value)"
              class="rounded-lg border border-ink-200/70 px-2 py-1 text-xs dark:border-ink-800/70"
            >
              @for (option of visibilityOptions; track option) {
                <option [value]="option">{{ option }}</option>
              }
            </select>
          </div>
          @if (visibilityError()) {
            <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
              {{ 'chat.adminPanel.actionError' | transloco }}
            </p>
          }
        </div>

        <div>
          <h3 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
            {{ 'chat.directory.peopleTitle' | transloco }}
          </h3>
          <ul class="flex flex-col gap-2">
            @for (userId of otherParticipantIds(); track userId) {
              <li
                class="flex flex-col gap-1 rounded-lg border border-ink-200/70 px-3 py-2 text-sm dark:border-ink-800/70"
              >
                <div class="flex items-center justify-between gap-2">
                  <span>{{ nicknameOf(userId) }}</span>
                  <div class="flex gap-2">
                    @if (!isParticipantAdmin(userId)) {
                      <button
                        type="button"
                        [attr.data-testid]="'promote-' + userId"
                        [attr.aria-label]="
                          'chat.adminPanel.promoteAriaLabel'
                            | transloco: { nickname: nicknameOf(userId) }
                        "
                        (click)="promote(userId)"
                        class="rounded-lg px-2 py-1 text-xs font-medium text-ink-700 dark:text-ink-200"
                      >
                        {{ 'chat.adminPanel.promote' | transloco }}
                      </button>
                    }
                    @if (confirmingRemoveUserId() === userId) {
                      <button
                        type="button"
                        [attr.data-testid]="'confirm-remove-' + userId"
                        (click)="confirmRemove(userId)"
                        class="rounded-lg bg-red-600 px-2 py-1 text-xs font-medium text-white"
                      >
                        {{ 'common.confirm' | transloco }}
                      </button>
                    } @else {
                      <button
                        type="button"
                        [attr.data-testid]="'remove-' + userId"
                        [attr.aria-label]="
                          'chat.adminPanel.removeAriaLabel'
                            | transloco: { nickname: nicknameOf(userId) }
                        "
                        (click)="confirmingRemoveUserId.set(userId)"
                        class="rounded-lg px-2 py-1 text-xs font-medium text-red-600"
                      >
                        {{ 'chat.adminPanel.remove' | transloco }}
                      </button>
                    }
                  </div>
                </div>
                @if (participantErrors().get(userId); as message) {
                  <p role="alert" class="text-xs text-red-600 dark:text-red-400">{{ message }}</p>
                }
              </li>
            }
          </ul>
        </div>

        <div>
          @if (confirmingDelete()) {
            <button
              type="button"
              data-testid="confirm-delete-group"
              (click)="confirmDelete()"
              class="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white"
            >
              {{ 'common.confirm' | transloco }}
            </button>
          } @else {
            <button
              type="button"
              data-testid="delete-group"
              [attr.aria-label]="'chat.adminPanel.deleteGroup' | transloco"
              (click)="confirmingDelete.set(true)"
              class="rounded-lg px-3 py-1.5 text-sm font-medium text-red-600"
            >
              {{ 'chat.adminPanel.deleteGroup' | transloco }}
            </button>
          }
          @if (deleteError()) {
            <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
              {{ 'chat.adminPanel.actionError' | transloco }}
            </p>
          }
        </div>
      </section>
    }
  `,
})
export class GroupAdminPanelComponent {
  private readonly chatGroupService = inject(ChatGroupService);

  readonly detail = input.required<ConversationDetail>();
  readonly currentUserId = input<number | null>(null);

  protected readonly visibilityOptions = VISIBILITY_VALUES;

  protected readonly isAdmin = computed(() => {
    const currentUserId = this.currentUserId();
    return currentUserId !== null && this.detail().adminUserIds.includes(currentUserId);
  });

  protected readonly otherParticipantIds = computed(() =>
    this.detail().participantUserIds.filter((id) => id !== this.currentUserId()),
  );

  protected readonly pendingRequests = computed(
    () => this.chatGroupService.pendingJoinRequests().get(this.detail().id) ?? [],
  );

  protected readonly confirmingRemoveUserId = signal<number | null>(null);
  protected readonly confirmingDelete = signal(false);
  protected readonly participantErrors = signal<Map<number, string>>(new Map());
  protected readonly requestErrors = signal<Map<number, string>>(new Map());
  protected readonly visibilityError = signal(false);
  protected readonly deleteError = signal(false);

  constructor() {
    effect(() => {
      const detail = this.detail();
      if (this.isAdmin()) {
        this.chatGroupService.fetchPendingJoinRequests(detail.id);
      }
    });
  }

  protected nicknameOf(userId: number): string {
    const entry = Object.entries(this.detail().participantNicknames).find(
      ([id]) => Number(id) === userId,
    );
    return entry?.[1] ?? String(userId);
  }

  protected isParticipantAdmin(userId: number): boolean {
    return this.detail().adminUserIds.includes(userId);
  }

  protected approve(requestId: number): void {
    const id = this.detail().id;
    this.chatGroupService.approveJoinRequest(id, requestId).subscribe({
      error: (err: { status?: number }) => {
        const message =
          err?.status === 400
            ? 'chat.adminPanel.notApprovableAnymore'
            : 'chat.adminPanel.actionError';
        this.requestErrors.update((map) => new Map(map).set(requestId, message));
      },
    });
  }

  protected reject(requestId: number): void {
    const id = this.detail().id;
    this.chatGroupService.rejectJoinRequest(id, requestId).subscribe({
      error: () =>
        this.requestErrors.update((map) =>
          new Map(map).set(requestId, 'chat.adminPanel.actionError'),
        ),
    });
  }

  protected promote(userId: number): void {
    const id = this.detail().id;
    this.chatGroupService.promote(id, userId).subscribe({
      error: () =>
        this.participantErrors.update((map) =>
          new Map(map).set(userId, 'chat.adminPanel.actionError'),
        ),
    });
  }

  protected confirmRemove(userId: number): void {
    const id = this.detail().id;
    this.confirmingRemoveUserId.set(null);
    this.chatGroupService.removeParticipant(id, userId).subscribe({
      error: () =>
        this.participantErrors.update((map) =>
          new Map(map).set(userId, 'chat.adminPanel.actionError'),
        ),
    });
  }

  protected onVisibilityChange(value: ChatGroupVisibility): void {
    this.visibilityError.set(false);
    const id = this.detail().id;
    this.chatGroupService.changeVisibility(id, value).subscribe({
      error: () => this.visibilityError.set(true),
    });
  }

  protected confirmDelete(): void {
    this.confirmingDelete.set(false);
    const id = this.detail().id;
    this.chatGroupService.deleteGroup(id).subscribe({
      error: () => this.deleteError.set(true),
    });
  }
}
