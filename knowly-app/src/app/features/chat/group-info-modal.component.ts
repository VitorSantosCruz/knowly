import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { EMPTY, catchError } from 'rxjs';
import { ChatGroupService } from '../../core/chat-group.service';
import { ChatService } from '../../core/chat.service';
import { CandidateUser, ConversationDetail } from '../../core/chat.model';
import { ParticipantPickerComponent } from './participant-picker.component';
import { GroupVisibilityBadgeComponent } from './group-visibility-badge.component';
import { GroupAdminPanelComponent } from './group-admin-panel.component';

/**
 * Group chat header "info" modal (2026-08-09 UX follow-up): everything that used to sit
 * always-visible under the conversation header for a `PEER_GROUP` conversation now lives here,
 * behind the header's own icon+name click — name/visibility, the member list, "invite someone"
 * (REQ: new UI wiring `ChatGroupService.addParticipants`, which already existed unused in the
 * service layer — no new backend endpoint), the full `GroupAdminPanelComponent` (visibility
 * change/pending join requests/promote/remove/delete, self-gated on `isAdmin`), and "Leave
 * group" (moved here verbatim from `conversation-detail.component.ts`, same
 * `ChatGroupService.leave` + navigate-away-on-success/inline-error-on-failure behavior).
 *
 * `ConversationDetail` has no `description` field on the wire (checked chat.model.ts/PLAN.md's
 * "Consumed API contracts") — showing one is new backend scope, out of bounds here; this only
 * shows name + visibility, per the product owner's explicit "don't invent it now" instruction.
 */
@Component({
  selector: 'app-group-info-modal',
  imports: [
    TranslocoPipe,
    GroupVisibilityBadgeComponent,
    ParticipantPickerComponent,
    GroupAdminPanelComponent,
  ],
  template: `
    <dialog
      #dialog
      data-testid="group-info-modal"
      class="fixed inset-0 m-auto w-full max-w-lg overflow-y-auto rounded-2xl border border-ink-200/70 p-6 shadow-lg shadow-ink-900/5 backdrop:bg-ink-950/60 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      (cancel)="onNativeDialogCancel($event)"
    >
      @if (open() && detail(); as detail) {
        <header class="mb-4 flex items-center gap-2">
          <h2 class="font-semibold text-ink-900 dark:text-white">
            {{ detail.title ?? ('chat.list.title' | transloco) }}
          </h2>
          <app-group-visibility-badge [visibility]="detail.visibility" />
        </header>

        <section class="mb-5">
          <h3 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
            {{ 'chat.groupInfoModal.membersTitle' | transloco }}
          </h3>
          <ul class="flex flex-col gap-1">
            @for (userId of detail.participantUserIds; track userId) {
              <li
                data-testid="group-info-modal-member"
                class="text-sm text-ink-700 dark:text-ink-300"
              >
                {{ nicknameOf(detail, userId) }}
              </li>
            }
          </ul>
        </section>

        <section class="mb-5">
          @if (!inviting()) {
            <button
              type="button"
              data-testid="group-info-modal-invite-toggle"
              class="rounded-lg border border-ink-200/70 px-3 py-1.5 text-sm dark:border-ink-800/70"
              (click)="startInviting(detail)"
            >
              {{ 'chat.groupInfoModal.inviteButton' | transloco }}
            </button>
          } @else {
            <h3 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
              {{ 'chat.groupInfoModal.inviteButton' | transloco }}
            </h3>
            <app-participant-picker
              [candidates]="candidates()"
              (selectionChange)="selectedIds.set($event)"
            />
            <button
              type="button"
              data-testid="group-info-modal-invite-submit"
              [disabled]="selectedIds().length === 0"
              class="mt-2 rounded-lg bg-signal-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              (click)="submitInvite(detail)"
            >
              {{ 'chat.groupInfoModal.addSelected' | transloco }}
            </button>
            @if (inviteError()) {
              <p role="alert" class="mt-1 text-sm text-red-600 dark:text-red-400">
                {{ 'chat.adminPanel.actionError' | transloco }}
              </p>
            }
          }
        </section>

        <app-group-admin-panel
          [detail]="detail"
          [currentUserId]="currentUserId()"
          (groupDeleted)="onGroupDeleted()"
        />

        <!-- REQ-16: only a genuine participant can leave -- a LOOKING_IN oversight-only
             viewer (support/admin present without a real chat_participants row) never gets
             this action, same gating conversation-detail.component.ts used to do inline. -->
        @if (isGenuineParticipant()) {
          <div class="mt-5 border-t border-ink-200/70 pt-4 dark:border-ink-800/70">
            @if (confirmingLeave()) {
              <button
                type="button"
                data-testid="confirm-leave-group"
                (click)="confirmLeave(detail)"
                class="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white"
              >
                {{ 'common.confirm' | transloco }}
              </button>
            } @else {
              <button
                type="button"
                data-testid="leave-group"
                [attr.aria-label]="'chat.adminPanel.leaveGroup' | transloco"
                (click)="confirmingLeave.set(true)"
                class="rounded-lg px-3 py-1.5 text-sm font-medium text-red-600"
              >
                {{ 'chat.adminPanel.leaveGroup' | transloco }}
              </button>
            }
            @if (leaveError()) {
              <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                {{ 'chat.adminPanel.actionError' | transloco }}
              </p>
            }
          </div>
        }
      }

      <div class="mt-4 flex justify-end">
        <button
          type="button"
          data-testid="group-info-modal-close"
          class="rounded-lg px-3 py-1.5 text-sm font-medium text-ink-700 transition-colors duration-fast ease-fluid hover:bg-ink-100 dark:text-ink-200 dark:hover:bg-ink-800"
          (click)="dismissed.emit()"
        >
          {{ 'common.close' | transloco }}
        </button>
      </div>
    </dialog>
  `,
})
export class GroupInfoModalComponent {
  private readonly chatService = inject(ChatService);
  private readonly chatGroupService = inject(ChatGroupService);
  private readonly router = inject(Router);

  readonly open = input<boolean>(false);
  readonly detail = input<ConversationDetail | null>(null);
  readonly currentUserId = input<number | null>(null);
  readonly dismissed = output<void>();

  protected readonly inviting = signal(false);
  protected readonly candidates = signal<CandidateUser[]>([]);
  protected readonly selectedIds = signal<number[]>([]);
  protected readonly inviteError = signal(false);

  protected readonly confirmingLeave = signal(false);
  protected readonly leaveError = signal(false);

  protected readonly isGenuineParticipant = computed(() => {
    const currentUserId = this.currentUserId();
    return (
      currentUserId !== null && (this.detail()?.participantUserIds ?? []).includes(currentUserId)
    );
  });

  private readonly dialogRef = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private wasOpen = false;

  constructor() {
    effect(() => {
      const dialog = this.dialogRef().nativeElement;
      const isOpen = this.open();

      if (isOpen && !dialog.open) {
        if (typeof dialog.showModal === 'function') {
          dialog.showModal();
        } else {
          dialog.setAttribute('open', '');
        }
      } else if (!isOpen && dialog.open) {
        if (typeof dialog.close === 'function') {
          dialog.close();
        } else {
          dialog.removeAttribute('open');
        }
      }

      if (isOpen && !this.wasOpen) {
        this.inviting.set(false);
        this.selectedIds.set([]);
        this.inviteError.set(false);
        this.confirmingLeave.set(false);
        this.leaveError.set(false);
      }
      this.wasOpen = isOpen;
    });
  }

  protected nicknameOf(detail: ConversationDetail, userId: number): string {
    const entry = Object.entries(detail.participantNicknames).find(([id]) => Number(id) === userId);
    return entry?.[1] ?? String(userId);
  }

  protected startInviting(detail: ConversationDetail): void {
    this.inviting.set(true);
    this.inviteError.set(false);
    this.chatService
      .getEligibleParticipants('group', detail.tenantId ?? undefined)
      .subscribe((candidates) => this.candidates.set(candidates));
  }

  protected submitInvite(detail: ConversationDetail): void {
    if (this.selectedIds().length === 0) {
      return;
    }

    this.inviteError.set(false);
    this.chatGroupService
      .addParticipants(detail.id, this.selectedIds())
      .pipe(
        catchError(() => {
          this.inviteError.set(true);
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.inviting.set(false);
        this.selectedIds.set([]);
      });
  }

  protected onGroupDeleted(): void {
    this.dismissed.emit();
    this.router.navigate(['/chat']);
  }

  protected confirmLeave(detail: ConversationDetail): void {
    this.confirmingLeave.set(false);
    this.leaveError.set(false);
    this.chatGroupService.leave(detail.id).subscribe({
      next: () => {
        this.dismissed.emit();
        this.router.navigate(['/chat']);
      },
      error: () => this.leaveError.set(true),
    });
  }

  protected onNativeDialogCancel(event: Event): void {
    event.preventDefault();
    this.dismissed.emit();
  }
}
