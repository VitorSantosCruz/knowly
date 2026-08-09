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
import { ConversationService } from '../../core/conversation.service';
import { IconKey } from '../../core/chat.model';
import { IconPickerComponent } from '../../shared/chat/icon-picker.component';

/**
 * REQ-38 (Amendment (4)): "Falar com a base de artigos" now opens this naming dialog instead of
 * silently creating an unnamed RAG conversation — mirrors `create-group-dialog.component.ts`'s
 * shape exactly (native `<dialog>`, template-driven signal-bound state, submit disabled until
 * name is non-blank), plus the shared `icon-picker.component.ts` for the optional icon.
 */
@Component({
  selector: 'app-create-conversation-dialog',
  imports: [TranslocoPipe, IconPickerComponent],
  template: `
    <dialog
      #dialog
      data-testid="create-conversation-dialog"
      class="fixed inset-0 m-auto w-full max-w-sm rounded-2xl border border-ink-200/70 p-6 shadow-lg shadow-ink-900/5 backdrop:bg-ink-950/60 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      (cancel)="onNativeDialogCancel($event)"
    >
      <h2 class="mb-4 font-semibold text-ink-900 dark:text-white">
        {{ 'chat.createConversation.title' | transloco }}
      </h2>

      <label class="mb-3 flex flex-col gap-1 text-sm">
        {{ 'chat.createConversation.nameLabel' | transloco }}
        <input
          type="text"
          data-testid="create-conversation-name-input"
          [attr.aria-label]="'chat.createConversation.nameLabel' | transloco"
          [value]="name()"
          (input)="name.set($any($event.target).value)"
          placeholder="{{ 'chat.createConversation.namePlaceholder' | transloco }}"
          class="rounded-lg border border-ink-200/70 px-2 py-1 dark:border-ink-800/70"
        />
      </label>

      <div class="mb-4 flex flex-col gap-1 text-sm">
        <span>{{ 'chat.iconPicker.label' | transloco }}</span>
        <app-icon-picker
          [selected]="icon()"
          [groupLabel]="'chat.iconPicker.label' | transloco"
          (iconSelected)="icon.set($event)"
        />
      </div>

      @if (error()) {
        <p
          data-testid="create-conversation-error"
          role="alert"
          class="mb-4 text-sm text-red-600 dark:text-red-400"
        >
          {{ 'chat.createConversation.error' | transloco }}
        </p>
      }

      <div class="flex justify-end gap-2">
        <button
          type="button"
          data-testid="create-conversation-cancel"
          [attr.aria-label]="'chat.createConversation.cancel' | transloco"
          (click)="cancel()"
        >
          {{ 'chat.createConversation.cancel' | transloco }}
        </button>
        <button
          type="button"
          data-testid="create-conversation-submit"
          [attr.aria-label]="'chat.createConversation.create' | transloco"
          [disabled]="submitDisabled()"
          (click)="submit()"
          class="rounded-lg bg-signal-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
        >
          {{ 'chat.createConversation.create' | transloco }}
        </button>
      </div>
    </dialog>
  `,
})
export class CreateConversationDialogComponent {
  private readonly conversationService = inject(ConversationService);
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly router = inject(Router);

  readonly open = input<boolean>(false);
  readonly dismissed = output<void>();

  protected readonly name = signal('');
  protected readonly icon = signal<IconKey | null>(null);
  protected readonly error = signal(false);

  protected readonly submitDisabled = computed(() => this.name().trim().length === 0);

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
        this.icon.set(null);
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
    const tenantId = this.activeTenantService.activeTenantId();
    if (this.submitDisabled() || tenantId === null) {
      return;
    }

    this.error.set(false);
    const icon = this.icon();
    this.conversationService.create(tenantId, this.name().trim(), icon ?? undefined).subscribe({
      next: (conversation) => this.router.navigate(['/chat/articles', conversation.id]),
      error: () => this.error.set(true),
    });
  }
}
