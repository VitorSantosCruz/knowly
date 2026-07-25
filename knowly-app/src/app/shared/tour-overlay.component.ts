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
        class="fixed inset-0 z-50 bg-slate-900/60"
        (keydown)="onKeydown($event)"
        #overlay
      >
        <div
          class="absolute w-80 rounded-2xl border border-slate-200 bg-white p-6 shadow-xl dark:border-slate-800 dark:bg-slate-900"
          [style.top.px]="position().top"
          [style.left.px]="position().left"
        >
          <h2 class="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
            {{ step().titleKey | transloco }}
          </h2>
          <p class="mb-6 text-sm text-slate-600 dark:text-slate-300">
            {{ step().bodyKey | transloco }}
          </p>
          <div class="flex items-center justify-between">
            <button
              type="button"
              data-testid="tour-skip"
              class="text-sm text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
              (click)="tourService.skip()"
            >
              {{ 'tour.skip' | transloco }}
            </button>
            <div class="flex gap-2">
              @if (tourService.stepIndex() > 0) {
                <button
                  type="button"
                  data-testid="tour-back"
                  class="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800"
                  (click)="tourService.back()"
                >
                  {{ 'tour.back' | transloco }}
                </button>
              }
              <button
                type="button"
                data-testid="tour-next"
                class="rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-500"
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
