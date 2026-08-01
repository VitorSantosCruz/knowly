import {
  Component,
  ElementRef,
  computed,
  effect,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-confirm-dialog',
  imports: [TranslocoPipe],
  template: `
    <dialog
      #dialog
      data-testid="confirm-dialog"
      class="fixed inset-0 m-auto w-full max-w-sm rounded-2xl border border-ink-200/70 p-6 shadow-lg shadow-ink-900/5 backdrop:bg-ink-950/60 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      (cancel)="onNativeDialogCancel($event)"
    >
      <p class="mb-4 text-sm text-ink-700 dark:text-ink-300">{{ message() }}</p>

      @if (loading()) {
        <p data-testid="confirm-dialog-loading" class="mb-4 text-sm text-ink-500 dark:text-ink-400">
          {{ 'common.confirmDialog.loading' | transloco }}
        </p>
      } @else if (fetchError()) {
        <div class="mb-4">
          <p
            data-testid="confirm-dialog-fetch-error"
            class="mb-2 text-sm text-red-700 dark:text-red-300"
          >
            {{ 'common.confirmDialog.fetchError' | transloco }}
          </p>
          <button
            type="button"
            data-testid="confirm-dialog-retry"
            class="rounded-lg px-3 py-1.5 text-sm font-medium text-ink-700 transition-colors duration-fast ease-fluid hover:bg-ink-100 dark:text-ink-200 dark:hover:bg-ink-800"
            (click)="requestToken()"
          >
            {{ 'common.confirmDialog.retry' | transloco }}
          </button>
        </div>
      } @else if (word(); as confirmationWord) {
        @if (invalidWordNotice()) {
          <p
            data-testid="confirm-dialog-invalid-word"
            class="mb-2 text-sm text-red-700 dark:text-red-300"
          >
            {{ 'common.confirmDialog.invalidWord' | transloco }}
          </p>
        }
        <p
          data-testid="confirm-dialog-word"
          class="mb-2 rounded-lg bg-ink-100 px-3 py-2 text-center font-mono text-sm font-semibold tracking-wide text-ink-900 dark:bg-ink-800 dark:text-white"
        >
          {{ confirmationWord }}
        </p>
        <label class="mb-4 block text-sm">
          <span class="sr-only">{{ 'common.confirmDialog.inputLabel' | transloco }}</span>
          <input
            type="text"
            data-testid="confirm-dialog-input"
            [attr.aria-label]="'common.confirmDialog.inputLabel' | transloco"
            placeholder="{{ 'common.confirmDialog.inputPlaceholder' | transloco }}"
            [value]="typed()"
            (input)="typed.set($any($event.target).value)"
            (paste)="$event.preventDefault()"
            (drop)="$event.preventDefault()"
            (dragover)="$event.preventDefault()"
            class="w-full rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white"
          />
        </label>
      }

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
          [disabled]="confirmDisabled()"
          class="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white transition-colors duration-fast ease-fluid hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-50"
          (click)="onConfirm()"
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
  /**
   * Called by the dialog itself when it opens (and on `retryToken`
   * bumps) to fetch a fresh deletion confirmation word. Function-typed
   * so each of the six call sites can close over its own resource
   * identity without the dialog needing six different input shapes.
   */
  readonly fetchToken = input.required<() => Observable<string>>();
  /**
   * Bumped by the caller on every REQ-8 "invalid/expired/used token"
   * 400 from the delete call. Any transition to a value different from
   * the last-seen one is treated as "the word you just used was
   * rejected" and triggers a fresh fetch.
   */
  readonly retryToken = input<number>(0);

  /** Emits the matched, retyped word. */
  readonly confirm = output<string>();
  /**
   * Fires on explicit "Cancel", backdrop dismiss, or Escape. Named
   * `dismissed` rather than `cancel` to satisfy
   * `@angular-eslint/no-output-native` (`cancel` is a native DOM event
   * name — see the `<dialog>`'s own `cancel` event below, which this
   * output re-emits).
   */
  readonly dismissed = output<void>();

  protected readonly word = signal<string | null>(null);
  protected readonly typed = signal('');
  protected readonly loading = signal(false);
  protected readonly fetchError = signal(false);
  protected readonly invalidWordNotice = signal(false);

  protected readonly confirmDisabled = computed(
    () => this.loading() || this.word() === null || this.typed() !== this.word(),
  );

  private readonly dialogRef = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private wasOpen = false;
  private lastRetryToken = 0;

  constructor() {
    effect(() => {
      const dialog = this.dialogRef().nativeElement;
      const isOpen = this.open();

      if (isOpen) {
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

      if (isOpen && !this.wasOpen) {
        this.resetState();
        this.requestToken();
      }
      this.wasOpen = isOpen;
    });

    effect(() => {
      const retryToken = this.retryToken();

      if (retryToken > 0 && retryToken !== this.lastRetryToken) {
        this.lastRetryToken = retryToken;
        this.word.set(null);
        this.typed.set('');
        this.invalidWordNotice.set(true);
        this.requestToken();
      }
    });
  }

  protected requestToken(): void {
    this.loading.set(true);
    this.fetchError.set(false);

    this.fetchToken()().subscribe({
      next: (word) => {
        this.word.set(word);
        this.loading.set(false);
      },
      error: () => {
        this.fetchError.set(true);
        this.loading.set(false);
      },
    });
  }

  protected onConfirm(): void {
    const word = this.word();

    if (this.confirmDisabled() || word === null) {
      return;
    }

    this.confirm.emit(word);
  }

  protected onNativeDialogCancel(event: Event): void {
    event.preventDefault();
    this.dismissed.emit();
  }

  private resetState(): void {
    this.word.set(null);
    this.typed.set('');
    this.fetchError.set(false);
    this.invalidWordNotice.set(false);
    this.lastRetryToken = 0;
  }
}
