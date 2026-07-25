import { Component, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TourService } from '../core/tour.service';

@Component({
  selector: 'app-help-menu',
  imports: [TranslocoPipe],
  template: `
    <div data-tour-id="help-menu" class="relative">
      <button
        type="button"
        data-testid="help-menu-toggle"
        [attr.aria-expanded]="open()"
        (click)="open.set(!open())"
        class="rounded-full px-3 py-1.5 text-sm text-ink-600 transition-colors duration-fast ease-fluid hover:bg-ink-200/70 hover:text-ink-900 dark:text-ink-300 dark:hover:bg-ink-800 dark:hover:text-white"
      >
        {{ 'helpMenu.label' | transloco }}
      </button>
      @if (open()) {
        <div
          role="menu"
          class="enter-fluid absolute top-full right-0 mt-1 min-w-40 rounded-xl border border-ink-200/70 bg-white p-1 shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
        >
          <button
            type="button"
            role="menuitem"
            data-testid="restart-tour"
            class="w-full rounded-lg px-3 py-2 text-left text-sm text-ink-700 transition-colors duration-fast ease-fluid hover:bg-ink-100 dark:text-ink-200 dark:hover:bg-ink-800"
            (click)="restartTour()"
          >
            {{ 'helpMenu.restartTour' | transloco }}
          </button>
        </div>
      }
    </div>
  `,
})
export class HelpMenuComponent {
  private readonly tourService = inject(TourService);

  protected readonly open = signal(false);

  protected restartTour(): void {
    this.open.set(false);
    this.tourService.start();
  }
}
