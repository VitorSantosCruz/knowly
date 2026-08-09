import { Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ICON_KEYS, IconKey } from '../../core/chat.model';
import { ICON_REGISTRY } from '../../core/chat-icon-registry';
import { ChatIconComponent } from './chat-icon.component';

/**
 * Amendment (4), REQ-38/REQ-39/REQ-40/REQ-13 (final round): a fixed 24-icon grid picker, reused
 * by the RAG creation dialog, the RAG/group rename inline forms, and `create-group-dialog
 * .component.ts` (PLAN.md's "one shared picker, three call sites" decision). A single-value
 * picker, not part of a larger reactive form — signal `input`/`output`, no `ControlValueAccessor`
 * needed.
 *
 * **[AppSec-added, 2026-08-09]** `iconSelected` only ever emits one of the 24 `IconKey` literal
 * values drawn from `ICON_KEYS` — no free-text/manual-entry path, and no `string`-typed
 * intermediate widens the type before emission (defense in depth on top of the backend's own
 * `400` validation for an invalid icon key).
 */
@Component({
  selector: 'app-icon-picker',
  imports: [TranslocoPipe, ChatIconComponent],
  template: `
    <div
      data-testid="icon-picker"
      role="group"
      [attr.aria-label]="groupLabel()"
      class="grid grid-cols-6 gap-1.5"
    >
      @for (key of iconKeys; track key) {
        <button
          type="button"
          [attr.data-testid]="'icon-picker-option-' + key"
          [attr.aria-label]="registry[key].labelKey | transloco"
          [attr.aria-pressed]="selected() === key"
          (click)="iconSelected.emit(key)"
          class="flex h-9 w-9 items-center justify-center rounded-lg border border-ink-200/70 text-ink-600 hover:bg-ink-50 dark:border-ink-800/70 dark:text-ink-300 dark:hover:bg-ink-800"
          [class.bg-signal-50]="selected() === key"
          [class.border-signal-500]="selected() === key"
          [class.dark:bg-signal-900]="selected() === key"
        >
          <app-chat-icon [icon]="key" class="h-4 w-4" />
        </button>
      }
    </div>
  `,
})
export class IconPickerComponent {
  readonly selected = input<IconKey | null>(null);
  /** `role="group"`'s own `aria-label` — the host renders its own visible label text separately
   * (a `<div>`/`<span>`, never a `<label for>`, since this isn't a single form control the
   * `@angular-eslint/template/label-has-associated-control` rule can associate one with). */
  readonly groupLabel = input<string | null>(null);
  readonly iconSelected = output<IconKey>();

  protected readonly iconKeys = ICON_KEYS;
  protected readonly registry = ICON_REGISTRY;
}
