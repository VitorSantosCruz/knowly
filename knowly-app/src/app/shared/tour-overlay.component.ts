import { Component, ElementRef, computed, inject, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TourService } from '../core/tour.service';

@Component({
  selector: 'app-tour-overlay',
  imports: [TranslocoPipe],
  template: `
    @if (tourService.active()) {
      <div
        data-testid="tour-overlay"
        role="dialog"
        aria-modal="true"
        aria-live="assertive"
        class="fixed inset-0 z-50 bg-ink-950/60"
        (keydown)="onKeydown($event)"
        #overlay
      >
        <div
          class="enter-fluid absolute w-80 rounded-2xl border border-ink-200/70 bg-white p-6 shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
          [style.top.px]="position().top"
          [style.left.px]="position().left"
        >
          <h2 class="font-display mb-2 text-lg font-semibold text-ink-900 dark:text-white">
            {{ step().titleKey | transloco }}
          </h2>
          <p class="mb-6 text-sm text-ink-600 dark:text-ink-300">
            {{ step().bodyKey | transloco }}
          </p>
          <div class="flex items-center justify-between">
            <button
              type="button"
              data-testid="tour-skip"
              class="text-sm text-ink-500 transition-colors duration-fast ease-fluid hover:text-ink-700 dark:text-ink-400 dark:hover:text-ink-200"
              (click)="tourService.skip()"
            >
              {{ 'tour.skip' | transloco }}
            </button>
            <div class="flex gap-2">
              @if (tourService.stepIndex() > 0) {
                <button
                  type="button"
                  data-testid="tour-back"
                  class="rounded-lg px-3 py-1.5 text-sm font-medium text-ink-700 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-ink-100 active:translate-y-0 active:scale-[0.98] dark:text-ink-200 dark:hover:bg-ink-800"
                  (click)="tourService.back()"
                >
                  {{ 'tour.back' | transloco }}
                </button>
              }
              <button
                type="button"
                data-testid="tour-next"
                class="rounded-lg bg-ink-800 px-3 py-1.5 text-sm font-medium text-white shadow-sm shadow-ink-900/20 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-signal-600 hover:shadow-md active:translate-y-0 active:scale-[0.98] active:bg-signal-700 dark:bg-ink-600 dark:hover:bg-signal-500"
                (click)="tourService.next()"
              >
                {{ isLastStep() ? ('tour.finish' | transloco) : ('tour.next' | transloco) }}
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class TourOverlayComponent {
  protected readonly tourService = inject(TourService);

  private readonly overlayRef = viewChild<ElementRef<HTMLElement>>('overlay');

  protected readonly step = computed(() => this.tourService.steps[this.tourService.stepIndex()]);
  protected readonly isLastStep = computed(
    () => this.tourService.stepIndex() === this.tourService.steps.length - 1,
  );

  private static readonly BOX_WIDTH_PX = 320;
  private static readonly VIEWPORT_MARGIN_PX = 16;

  protected readonly position = computed(() => {
    const target = document.querySelector(`[data-tour-id="${this.step().targetId}"]`);

    if (!target) {
      return { top: window.innerHeight / 2 - 100, left: window.innerWidth / 2 - 160 };
    }

    const rect = target.getBoundingClientRect();
    const maxLeft =
      window.innerWidth -
      TourOverlayComponent.BOX_WIDTH_PX -
      TourOverlayComponent.VIEWPORT_MARGIN_PX;
    const left = Math.min(rect.left, Math.max(TourOverlayComponent.VIEWPORT_MARGIN_PX, maxLeft));

    return { top: rect.bottom + 12, left };
  });

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.tourService.skip();
      return;
    }

    if (event.key !== 'Tab') {
      return;
    }

    const overlay = this.overlayRef()?.nativeElement;
    if (!overlay) {
      return;
    }

    const focusable = Array.from(overlay.querySelectorAll<HTMLElement>('button'));
    if (focusable.length === 0) {
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (!event.shiftKey && event.target === last) {
      event.preventDefault();
      first.focus();
    } else if (event.shiftKey && event.target === first) {
      event.preventDefault();
      last.focus();
    }
  }
}
