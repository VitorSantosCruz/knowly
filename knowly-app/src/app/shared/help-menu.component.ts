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
        class="rounded-full px-3 py-1.5 text-sm transition hover:bg-slate-200/70 dark:hover:bg-slate-800"
      >
        {{ 'helpMenu.label' | transloco }}
      </button>
      @if (open()) {
        <div
          role="menu"
          class="absolute top-full right-0 mt-1 min-w-40 rounded-xl border border-slate-200 bg-white p-1 shadow-lg dark:border-slate-800 dark:bg-slate-900"
        >
          <button
            type="button"
            role="menuitem"
            data-testid="restart-tour"
            class="w-full rounded-lg px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800"
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
