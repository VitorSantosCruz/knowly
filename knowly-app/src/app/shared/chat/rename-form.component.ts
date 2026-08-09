import { Component, computed, effect, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { IconKey } from '../../core/chat.model';
import { IconPickerComponent } from './icon-picker.component';

/**
 * Amendment (4), REQ-39/REQ-40: the shared inline rename form (name input + `icon-picker
 * .component.ts`, prefilled, save/cancel) — reused by both the RAG rename affordance
 * (`conversations-page.component.ts`) and the group rename affordance (`chat-header.component
 * .ts`), per PLAN.md's "reusing the same presentational sub-component as 13d rather than a
 * second copy" decision. Purely presentational: the host owns the actual
 * `ConversationService.rename`/`ChatGroupService.rename` call and error handling — this
 * component only emits `{ title, icon }` on save.
 *
 * **[AppSec-added, 2026-08-09]** `error` is a plain boolean, not a status-code-derived message —
 * the host is responsible for rendering one shared, status-code-agnostic error string per surface
 * (never a more specific message on a `404`/`403` than on a `400`/network failure), this
 * component just reserves the slot.
 */
@Component({
  selector: 'app-rename-form',
  imports: [TranslocoPipe, IconPickerComponent],
  template: `
    <form data-testid="rename-form" class="flex flex-col gap-2" (submit)="onSubmit($event)">
      <input
        type="text"
        data-testid="rename-form-name-input"
        [attr.aria-label]="'chat.rename.nameLabel' | transloco"
        [value]="name()"
        (input)="name.set($any($event.target).value)"
        placeholder="{{ 'chat.rename.namePlaceholder' | transloco }}"
        class="rounded-lg border border-ink-200/70 px-2 py-1 text-sm dark:border-ink-800/70"
      />
      <app-icon-picker [selected]="icon()" (iconSelected)="icon.set($event)" />
      @if (error()) {
        <p
          data-testid="rename-form-error"
          role="alert"
          class="text-xs text-red-600 dark:text-red-400"
        >
          {{ 'chat.rename.error' | transloco }}
        </p>
      }
      <div class="flex gap-2">
        <button
          type="submit"
          data-testid="rename-form-save"
          [attr.aria-label]="'chat.rename.save' | transloco"
          [disabled]="saveDisabled()"
          class="rounded-lg bg-signal-600 px-2 py-1 text-xs font-medium text-white disabled:opacity-50"
        >
          {{ 'chat.rename.save' | transloco }}
        </button>
        <button
          type="button"
          data-testid="rename-form-cancel"
          [attr.aria-label]="'chat.rename.cancel' | transloco"
          (click)="cancelled.emit()"
          class="rounded-lg px-2 py-1 text-xs font-medium text-ink-700 dark:text-ink-200"
        >
          {{ 'chat.rename.cancel' | transloco }}
        </button>
      </div>
    </form>
  `,
})
export class RenameFormComponent {
  readonly initialTitle = input<string>('');
  readonly initialIcon = input<IconKey | null>(null);
  readonly error = input(false);

  readonly saved = output<{ title: string; icon: IconKey | null }>();
  readonly cancelled = output<void>();

  protected readonly name = signal('');
  protected readonly icon = signal<IconKey | null>(null);

  protected readonly saveDisabled = computed(() => this.name().trim().length === 0);

  private seeded = false;

  constructor() {
    // Read-once seed on first render only, per input.required-like intent — this is a
    // prefilled edit-in-place form, not a form that should reset on every parent change
    // detection once the viewer starts typing. `effect()` (not a field initializer/constructor
    // read) since `input()` values aren't guaranteed set until Angular's first change-detection
    // pass — a field initializer read would race `TestBed`'s `setInput()`/real template bindings.
    effect(() => {
      const title = this.initialTitle();
      const icon = this.initialIcon();
      if (!this.seeded) {
        this.seeded = true;
        this.name.set(title);
        this.icon.set(icon);
      }
    });
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    if (this.saveDisabled()) {
      return;
    }
    this.saved.emit({ title: this.name().trim(), icon: this.icon() });
  }
}
