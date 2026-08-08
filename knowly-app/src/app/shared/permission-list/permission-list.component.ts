import { Component, inject, input, output } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { translatePermissionDescription, translatePermissionLabel } from '../permission-labels';
import { PermissionListMode, PermissionListRow } from './permission-list.model';

// role-permission-management-ui: reusable, dumb, presentational permission list (SPEC req 1-3).
// Never performs an HTTP call itself — the caller decides what "toggle" means (see PLAN.md's
// "Architectural decisions"). Fully controlled by its `rows`/`mode` inputs, no internal state.
@Component({
  selector: 'app-permission-list',
  template: `
    <ul class="flex flex-col divide-y divide-ink-100 dark:divide-ink-800">
      @for (row of rows(); track row.value) {
        <li class="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
          <div class="min-w-0">
            <p class="text-sm font-medium text-ink-900 dark:text-white">
              {{ label(row.value) }}
            </p>
            <p class="mt-0.5 text-sm text-ink-500 dark:text-ink-400">
              {{ description(row.value) }}
            </p>
          </div>

          @if (mode() === 'editable') {
            <button
              type="button"
              role="switch"
              [attr.aria-checked]="row.granted"
              [attr.aria-label]="label(row.value)"
              [attr.data-testid]="'permission-list-toggle-' + row.value"
              [disabled]="disabled()"
              (click)="onToggle(row.value)"
              (keydown)="onKeydown($event, row.value)"
              [class]="
                'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors duration-fast ease-fluid disabled:pointer-events-none disabled:opacity-50 ' +
                (row.granted ? 'bg-signal-600' : 'bg-ink-300 dark:bg-ink-700')
              "
            >
              <span
                [class]="
                  'inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform duration-fast ease-fluid ' +
                  (row.granted ? 'translate-x-4' : 'translate-x-1')
                "
              ></span>
            </button>
          }
        </li>
      }
    </ul>
  `,
})
export class PermissionListComponent {
  private readonly transloco = inject(TranslocoService);

  readonly rows = input.required<PermissionListRow[]>();
  readonly mode = input<PermissionListMode>('readonly');
  // Not part of the PLAN's original PermissionListRow model — an editable-mode-wide lock (e.g.
  // "the viewer can't manage direct permissions right now"), distinct from `mode`, which controls
  // whether a switch renders at all. Defaults to false so no existing/four consumers need to pass
  // it unless they have a viewer-permission gate to apply.
  readonly disabled = input(false);

  readonly toggle = output<string>();

  protected label(value: string): string {
    return translatePermissionLabel(value, this.transloco);
  }

  protected description(value: string): string {
    return translatePermissionDescription(value, this.transloco);
  }

  protected onToggle(value: string): void {
    if (this.disabled()) {
      return;
    }

    this.toggle.emit(value);
  }

  protected onKeydown(event: KeyboardEvent, value: string): void {
    if (event.key !== 'Enter' && event.key !== ' ') {
      return;
    }

    event.preventDefault();

    if (this.disabled()) {
      return;
    }

    this.toggle.emit(value);
  }
}
