import { Component, inject, signal, computed } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { Menu } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import { TourService } from '../core/tour.service';

interface HelpMenuItem extends MenuItem {
  labelKey: string;
  testId: string;
}

@Component({
  selector: 'app-help-menu',
  imports: [TranslocoPipe, ButtonDirective, Menu],
  template: `
    <div data-tour-id="help-menu" class="relative">
      <button
        type="button"
        pButton
        text
        severity="secondary"
        data-testid="help-menu-toggle"
        [attr.aria-expanded]="open()"
        (click)="open.set(!open())"
      >
        {{ 'helpMenu.label' | transloco }}
      </button>
      @if (open()) {
        <p-menu
          [model]="items()"
          [popup]="false"
          styleClass="enter-fluid absolute top-full right-0 left-auto z-20 mt-1 min-w-40 rounded-xl border border-ink-800 bg-ink-900 p-1 shadow-lg shadow-ink-950/40"
        >
          <ng-template #item let-item>
            <button
              type="button"
              role="menuitem"
              [attr.data-testid]="item.testId"
              class="w-full rounded-lg px-3 py-2 text-left text-sm text-ink-200 transition-colors duration-fast ease-fluid hover:bg-ink-800"
              (click)="item.command && item.command({ originalEvent: $event, item })"
            >
              {{ item.labelKey | transloco }}
            </button>
          </ng-template>
        </p-menu>
      }
    </div>
  `,
})
export class HelpMenuComponent {
  private readonly tourService = inject(TourService);

  protected readonly open = signal(false);

  protected readonly items = computed<HelpMenuItem[]>(() => [
    {
      labelKey: 'helpMenu.restartTour',
      testId: 'restart-tour',
      command: () => this.restartTour(),
    },
  ]);

  protected restartTour(): void {
    this.open.set(false);
    this.tourService.start();
  }
}
