import { Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-confirm-dialog',
  imports: [TranslocoPipe],
  template: `
    <dialog
      #dialog
      data-testid="confirm-dialog"
      class="rounded-2xl border border-ink-200/70 p-6 shadow-lg shadow-ink-900/5 backdrop:bg-ink-950/60 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      (cancel)="onNativeDialogCancel($event)"
    >
      <p class="mb-6 text-sm text-ink-700 dark:text-ink-300">{{ message() }}</p>
      <div class="flex justify-end gap-2">
        <button
          type="button"
          data-testid="confirm-dialog-cancel"
          class="rounded-lg px-3 py-1.5 text-sm font-medium text-ink-700 transition-colors duration-fast ease-fluid hover:bg-ink-100 dark:text-ink-200 dark:hover:bg-ink-800"
          (click)="dismissed.emit()"
        >
          {{ 'common.cancel' | transloco }}
        </button>
        <button
          type="button"
          data-testid="confirm-dialog-confirm"
          class="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white transition-colors duration-fast ease-fluid hover:bg-red-500"
          (click)="confirm.emit()"
        >
          {{ 'common.confirm' | transloco }}
        </button>
      </div>
    </dialog>
  `,
})
export class ConfirmDialogComponent {
  readonly open = input<boolean>(false);
  readonly message = input<string>('');

  readonly confirm = output<void>();
  /**
   * Fires on explicit "Cancel", backdrop dismiss, or Escape. Named
   * `dismissed` rather than `cancel` to satisfy
   * `@angular-eslint/no-output-native` (`cancel` is a native DOM event
   * name — see the `<dialog>`'s own `cancel` event below, which this
   * output re-emits).
   */
  readonly dismissed = output<void>();

  private readonly dialogRef = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const dialog = this.dialogRef().nativeElement;

      if (this.open()) {
        if (!dialog.open) {
          if (typeof dialog.showModal === 'function') {
            dialog.showModal();
          } else {
            dialog.setAttribute('open', '');
          }
        }
      } else if (dialog.open) {
        if (typeof dialog.close === 'function') {
          dialog.close();
        } else {
          dialog.removeAttribute('open');
        }
      }
    });
  }

  protected onNativeDialogCancel(event: Event): void {
    event.preventDefault();
    this.dismissed.emit();
  }
}
