import { Component, ElementRef, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';

const MAX_HEIGHT_PX = 128; // matches the `max-h-32` class on the textarea

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
        #inputEl
        data-testid="message-composer-input"
        [attr.aria-label]="'chat.composer.inputLabel' | transloco"
        [ngModel]="value()"
        (ngModelChange)="value.set($event)"
        (input)="autoGrow(inputEl)"
        name="content"
        rows="1"
        [disabled]="disabled()"
        [class.overflow-y-auto]="overflowing()"
        [class.overflow-y-hidden]="!overflowing()"
        class="max-h-32 flex-1 resize-none rounded-lg border border-ink-200/70 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:outline-none disabled:opacity-50 dark:border-ink-800/70 dark:bg-ink-900 dark:text-white"
      ></textarea>
      <button
        type="submit"
        data-testid="message-composer-send"
        [attr.aria-label]="'chat.composer.send' | transloco"
        [disabled]="disabled() || !value().trim()"
        class="rounded-lg bg-signal-600 px-4 py-2 text-sm font-medium text-white transition-colors duration-base ease-fluid hover:bg-signal-700 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {{ 'chat.composer.send' | transloco }}
      </button>
    </form>
  `,
})
export class MessageComposerComponent {
  readonly disabled = input(false);
  readonly send = output<string>();

  protected readonly value = signal('');
  protected readonly overflowing = signal(false);
  private readonly inputEl = viewChild<ElementRef<HTMLTextAreaElement>>('inputEl');

  protected autoGrow(textarea: HTMLTextAreaElement): void {
    textarea.style.height = 'auto';
    textarea.style.height = `${textarea.scrollHeight}px`;
    // `overflow-y-auto` alone can leave a persistent 1px scrollbar sliver even under the cap,
    // from sub-pixel rounding between scrollHeight and the clamped clientHeight — only turn
    // scrolling on once content genuinely exceeds the max height.
    this.overflowing.set(textarea.scrollHeight > MAX_HEIGHT_PX);
  }

  onSubmit(): void {
    const content = this.value().trim();
    if (!content) {
      return;
    }
    this.send.emit(content);
    this.value.set('');

    const textarea = this.inputEl()?.nativeElement;
    if (textarea) {
      textarea.style.height = 'auto';
      this.overflowing.set(false);
    }
  }
}
