import { Component, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Single textarea + submit, signal-bound `[(ngModel)]` value — no `ReactiveFormsModule`,
 * per PLAN.md's "no existing precedent for a form-heavy single-field composer" call.
 */
@Component({
  selector: 'app-message-composer',
  imports: [FormsModule, TranslocoPipe],
  template: `
    <form
      data-testid="message-composer"
      class="flex items-end gap-2 border-t border-ink-200/70 pt-3 dark:border-ink-800/70"
      (ngSubmit)="onSubmit()"
    >
      <textarea
        data-testid="message-composer-input"
        [attr.aria-label]="'chat.composer.inputLabel' | transloco"
        [ngModel]="value()"
        (ngModelChange)="value.set($event)"
        name="content"
        rows="2"
        class="flex-1 resize-none rounded-lg border border-ink-200/70 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:outline-none dark:border-ink-800/70 dark:bg-ink-900 dark:text-white"
      ></textarea>
      <button
        type="submit"
        data-testid="message-composer-send"
        [attr.aria-label]="'chat.composer.send' | transloco"
        [disabled]="!value().trim()"
        class="rounded-lg bg-signal-600 px-4 py-2 text-sm font-medium text-white transition-colors duration-base ease-fluid hover:bg-signal-700 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {{ 'chat.composer.send' | transloco }}
      </button>
    </form>
  `,
})
export class MessageComposerComponent {
  readonly send = output<string>();

  protected readonly value = signal('');

  onSubmit(): void {
    const content = this.value().trim();
    if (!content) {
      return;
    }
    this.send.emit(content);
    this.value.set('');
  }
}
