import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChatService } from '../../core/chat.service';
import { EligibilityScope } from '../../core/chat.model';
import { ParticipantPickerComponent } from './participant-picker.component';

type Mode = 'direct' | 'member-group' | 'staff-group';

const SCOPE_BY_MODE: Record<Mode, EligibilityScope> = {
  direct: 'direct',
  'member-group': 'group',
  'staff-group': 'group-staff-only',
};

/** REQ-2/REQ-3 entry point (1:1 vs group toggle), driving `ParticipantPickerComponent` off
 * `ChatService.fetchEligibleParticipants`. */
@Component({
  selector: 'app-new-conversation-dialog',
  imports: [FormsModule, TranslocoPipe, ParticipantPickerComponent],
  template: `
    <div data-testid="new-conversation-dialog" class="flex flex-col gap-4">
      <h2 class="font-semibold text-ink-900 dark:text-white">
        {{ 'chat.newConversation.title' | transloco }}
      </h2>

      <div class="flex gap-2">
        <button
          type="button"
          data-testid="mode-direct"
          [class.font-bold]="mode() === 'direct'"
          (click)="setMode('direct')"
        >
          {{ 'chat.newConversation.modeDirect' | transloco }}
        </button>
        <button
          type="button"
          data-testid="mode-member-group"
          [class.font-bold]="mode() === 'member-group'"
          (click)="setMode('member-group')"
        >
          {{ 'chat.newConversation.modeMemberGroup' | transloco }}
        </button>
        <button
          type="button"
          data-testid="mode-staff-group"
          [class.font-bold]="mode() === 'staff-group'"
          (click)="setMode('staff-group')"
        >
          {{ 'chat.newConversation.modeStaffGroup' | transloco }}
        </button>
      </div>

      @if (mode() === 'member-group') {
        <label class="flex flex-col gap-1 text-sm">
          {{ 'chat.newConversation.tenantLabel' | transloco }}
          <input
            type="number"
            data-testid="tenant-id-input"
            [(ngModel)]="tenantIdValue"
            (ngModelChange)="onTenantChange($event)"
            name="tenantId"
            class="rounded-lg border border-ink-200/70 px-2 py-1 dark:border-ink-800/70"
          />
        </label>
      }

      <app-participant-picker
        [candidates]="chatService.eligibleParticipants()"
        [multi]="mode() !== 'direct'"
        (selectionChange)="selectedIds.set($event)"
      />

      @if (error()) {
        <p
          data-testid="new-conversation-error"
          role="alert"
          class="text-sm text-red-600 dark:text-red-400"
        >
          {{ 'chat.newConversation.error' | transloco }}
        </p>
      }

      <div class="flex justify-end gap-2">
        <button type="button" data-testid="cancel-button" (click)="cancel()">
          {{ 'chat.newConversation.cancel' | transloco }}
        </button>
        <button
          type="button"
          data-testid="create-button"
          [disabled]="selectedIds().length === 0"
          (click)="create()"
          class="rounded-lg bg-signal-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
        >
          {{ 'chat.newConversation.create' | transloco }}
        </button>
      </div>
    </div>
  `,
})
export class NewConversationDialogComponent {
  protected readonly chatService = inject(ChatService);
  private readonly router = inject(Router);

  protected readonly mode = signal<Mode>('direct');
  protected readonly selectedIds = signal<number[]>([]);
  protected readonly error = signal(false);
  protected tenantIdValue: number | null = null;

  constructor() {
    this.chatService.fetchEligibleParticipants('direct');
  }

  setMode(mode: Mode): void {
    this.mode.set(mode);
    this.selectedIds.set([]);
    if (mode === 'member-group') {
      if (this.tenantIdValue !== null) {
        this.chatService.fetchEligibleParticipants('group', this.tenantIdValue);
      }
    } else {
      this.chatService.fetchEligibleParticipants(SCOPE_BY_MODE[mode]);
    }
  }

  onTenantChange(tenantId: number): void {
    this.tenantIdValue = tenantId;
    this.chatService.fetchEligibleParticipants('group', tenantId);
  }

  cancel(): void {
    this.router.navigate(['/chat']);
  }

  create(): void {
    this.error.set(false);
    const mode = this.mode();
    const request =
      mode === 'direct'
        ? { kind: 'DIRECT' as const, participantUserIds: this.selectedIds() }
        : mode === 'member-group'
          ? {
              kind: 'GROUP' as const,
              tenantId: this.tenantIdValue,
              participantUserIds: this.selectedIds(),
            }
          : {
              kind: 'GROUP' as const,
              tenantId: null,
              participantUserIds: this.selectedIds(),
            };

    this.chatService.createConversation(request).subscribe({
      next: (conversation) => this.router.navigate(['/chat', conversation.id]),
      error: () => this.error.set(true),
    });
  }
}
