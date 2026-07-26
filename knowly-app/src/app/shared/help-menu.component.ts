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
        class="rounded-lg px-3 py-1.5 text-sm text-ink-300 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-ink-800/60 hover:text-white active:translate-y-0 active:scale-[0.98]"
      >
        {{ 'helpMenu.label' | transloco }}
      </button>
      @if (open()) {
        <div
          role="menu"
          class="enter-fluid absolute top-full right-0 left-auto z-20 mt-1 min-w-40 rounded-xl border border-ink-800 bg-ink-900 p-1 shadow-lg shadow-ink-950/40"
        >
          <button
            type="button"
            role="menuitem"
            data-testid="restart-tour"
            class="w-full rounded-lg px-3 py-2 text-left text-sm text-ink-200 transition-colors duration-fast ease-fluid hover:bg-ink-800"
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
