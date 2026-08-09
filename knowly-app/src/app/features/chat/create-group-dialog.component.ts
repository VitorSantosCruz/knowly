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
import { ActiveTenantService } from '../../core/active-tenant.service';
import { ChatService } from '../../core/chat.service';
import { ChatGroupVisibility } from '../../core/chat.model';
import { GroupVisibilityBadgeComponent } from './group-visibility-badge.component';

const VISIBILITY_OPTIONS: {
  value: ChatGroupVisibility;
  labelKey: string;
  descriptionKey: string;
}[] = [
  {
    value: 'PRIVATE',
    labelKey: 'chat.groupVisibility.private',
    descriptionKey: 'chat.createGroup.visibilityPrivateDescription',
  },
  {
    value: 'REQUEST_TO_JOIN',
    labelKey: 'chat.groupVisibility.requestToJoin',
    descriptionKey: 'chat.createGroup.visibilityRequestToJoinDescription',
  },
  {
    value: 'PUBLIC',
    labelKey: 'chat.groupVisibility.public',
    descriptionKey: 'chat.createGroup.visibilityPublicDescription',
  },
];

/**
 * REQ-12/13/18: dedicated "Criar grupo" flow — name + visibility only, no participant picker
 * (other participants are added afterward via `participant-picker.component.ts`, unchanged).
 * Native `<dialog>`, per this codebase's established first-modal precedent
 * (`ConfirmDialogComponent`) — signal-bound state, no forms module needed.
 */
@Component({
  selector: 'app-create-group-dialog',
  imports: [TranslocoPipe, GroupVisibilityBadgeComponent],
  template: `
    <dialog
      #dialog
      data-testid="create-group-dialog"
      class="fixed inset-0 m-auto w-full max-w-sm rounded-2xl border border-ink-200/70 p-6 shadow-lg shadow-ink-900/5 backdrop:bg-ink-950/60 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      (cancel)="onNativeDialogCancel($event)"
    >
      <h2 class="mb-4 font-semibold text-ink-900 dark:text-white">
        {{ 'chat.createGroup.title' | transloco }}
      </h2>

      <label class="mb-3 flex flex-col gap-1 text-sm">
        {{ 'chat.createGroup.nameLabel' | transloco }}
        <input
          type="text"
          data-testid="create-group-name-input"
          [attr.aria-label]="'chat.createGroup.nameLabel' | transloco"
          [value]="name()"
          (input)="name.set($any($event.target).value)"
          placeholder="{{ 'chat.createGroup.namePlaceholder' | transloco }}"
          class="rounded-lg border border-ink-200/70 px-2 py-1 dark:border-ink-800/70"
        />
      </label>

      <fieldset class="mb-4 flex flex-col gap-2">
        <legend class="mb-1 text-sm font-medium text-ink-700 dark:text-ink-300">
          {{ 'chat.createGroup.visibilityLabel' | transloco }}
        </legend>
        @for (option of visibilityOptions; track option.value) {
          <label
            class="flex items-start gap-2 rounded-lg border border-ink-200/70 px-3 py-2 text-sm dark:border-ink-800/70"
          >
            <input
              type="radio"
              name="visibility"
              [attr.data-testid]="'create-group-visibility-' + option.value"
              [attr.aria-label]="option.labelKey | transloco"
              [value]="option.value"
              [checked]="visibility() === option.value"
              (change)="visibility.set(option.value)"
            />
            <span>
              <app-group-visibility-badge [visibility]="option.value" />
              <span class="mt-1 block text-xs text-ink-500 dark:text-ink-400">{{
                option.descriptionKey | transloco
              }}</span>
            </span>
          </label>
        }
      </fieldset>

      @if (error()) {
        <p
          data-testid="create-group-error"
          role="alert"
          class="mb-4 text-sm text-red-600 dark:text-red-400"
        >
          {{ 'chat.createGroup.error' | transloco }}
        </p>
      }

      <div class="flex justify-end gap-2">
        <button
          type="button"
          data-testid="create-group-cancel"
          [attr.aria-label]="'chat.createGroup.cancel' | transloco"
          (click)="cancel()"
        >
          {{ 'chat.createGroup.cancel' | transloco }}
        </button>
        <button
          type="button"
          data-testid="create-group-submit"
          [attr.aria-label]="'chat.createGroup.create' | transloco"
          [disabled]="submitDisabled()"
          (click)="submit()"
          class="rounded-lg bg-signal-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
        >
          {{ 'chat.createGroup.create' | transloco }}
        </button>
      </div>
    </dialog>
  `,
})
export class CreateGroupDialogComponent {
  private readonly chatService = inject(ChatService);
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly router = inject(Router);

  readonly open = input<boolean>(false);
  readonly dismissed = output<void>();

  protected readonly visibilityOptions = VISIBILITY_OPTIONS;
  protected readonly name = signal('');
  protected readonly visibility = signal<ChatGroupVisibility | null>(null);
  protected readonly error = signal(false);

  protected readonly submitDisabled = computed(
    () => this.name().trim().length === 0 || this.visibility() === null,
  );

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
        this.name.set('');
        this.visibility.set(null);
        this.error.set(false);
      }
      this.wasOpen = isOpen;
    });
  }

  protected cancel(): void {
    this.dismissed.emit();
  }

  protected onNativeDialogCancel(event: Event): void {
    event.preventDefault();
    this.dismissed.emit();
  }

  protected submit(): void {
    const visibility = this.visibility();
    if (this.submitDisabled() || visibility === null) {
      return;
    }

    this.error.set(false);
    this.chatService
      .createConversation({
        kind: 'GROUP',
        tenantId: this.activeTenantService.activeTenantId(),
        title: this.name().trim(),
        visibility,
        participantUserIds: [],
      })
      .subscribe({
        next: (conversation) => this.router.navigate(['/chat', conversation.id]),
        error: () => this.error.set(true),
      });
  }
}
