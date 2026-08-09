import { Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ProfileSectionComponent } from '../user-management/profile-section.component';

/**
 * 1:1 chat header "who is this" modal — reuses `ProfileSectionComponent` (the same view already
 * shown for a member in `member-detail-panel.component.ts`) in pure view mode
 * (`canEdit=false`, `hideEditToggle=true`), rather than inventing a second profile card. A
 * viewer without `PROFILE_VIEW`/admin standing over the other participant gets that component's
 * own existing 403 -> `NoAccessStateComponent` fallback, not a crash — same "a permission-check
 * 403 is not an error" rule as everywhere else in this app.
 *
 * Native `<dialog>`, same open/close `effect()` shape as `ConfirmDialogComponent`/
 * `CreateGroupDialogComponent` (this codebase's established modal precedent).
 */
@Component({
  selector: 'app-person-info-modal',
  imports: [TranslocoPipe, ProfileSectionComponent],
  template: `
    <dialog
      #dialog
      data-testid="person-info-modal"
      class="fixed inset-0 m-auto w-full max-w-md rounded-2xl border border-ink-200/70 p-6 shadow-lg shadow-ink-900/5 backdrop:bg-ink-950/60 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      (cancel)="onNativeDialogCancel($event)"
    >
      @if (open() && userId(); as userId) {
        <app-profile-section [userId]="userId" [canEdit]="false" [hideEditToggle]="true" />
      }

      <div class="mt-4 flex justify-end">
        <button
          type="button"
          data-testid="person-info-modal-close"
          class="rounded-lg px-3 py-1.5 text-sm font-medium text-ink-700 transition-colors duration-fast ease-fluid hover:bg-ink-100 dark:text-ink-200 dark:hover:bg-ink-800"
          (click)="dismissed.emit()"
        >
          {{ 'common.close' | transloco }}
        </button>
      </div>
    </dialog>
  `,
})
export class PersonInfoModalComponent {
  readonly open = input<boolean>(false);
  readonly userId = input<number | null>(null);
  readonly dismissed = output<void>();

  private readonly dialogRef = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const dialog = this.dialogRef().nativeElement;
      const isOpen = this.open();

      if (isOpen && !dialog.open) {
        if (typeof dialog.showModal === 'function') {
          dialog.showModal();
        } else {
          dialog.setAttribute('open', '');
        }
      } else if (!isOpen && dialog.open) {
        if (typeof dialog.close === 'function') {
          dialog.close();
        } else {
          dialog.removeAttribute('open');
        }
      }
    });
  }

  protected onNativeDialogCancel(event: Event): void {
    event.preventDefault();
    this.dismissed.emit();
  }
}
