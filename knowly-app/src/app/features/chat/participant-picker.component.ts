import { Component, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { CandidateUser } from '../../core/chat.model';

/**
 * Renders whatever candidate list is passed to it as-is — no client-side staff/member/tenant
 * filtering (REQ-2/REQ-3: eligibility is a backend rule, never reimplemented here). Single or
 * multi-select over a signal-backed `Set<number>` of selected ids, per PLAN.md.
 */
@Component({
  selector: 'app-participant-picker',
  imports: [TranslocoPipe],
  template: `
    <ul data-testid="participant-picker" class="flex max-h-64 flex-col gap-1 overflow-y-auto">
      @for (candidate of candidates(); track candidate.userId) {
        <li>
          <label
            class="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-ink-50 dark:hover:bg-ink-800"
          >
            <input
              type="checkbox"
              data-testid="participant-picker-candidate"
              [attr.aria-label]="
                'chat.picker.candidateAriaLabel' | transloco: { nickname: candidate.nickname }
              "
              [checked]="selectedIds().has(candidate.userId)"
              (change)="toggle(candidate.userId)"
            />
            {{ candidate.nickname }}
          </label>
        </li>
      }
    </ul>
  `,
})
export class ParticipantPickerComponent {
  readonly candidates = input<CandidateUser[]>([]);
  readonly multi = input(true);
  readonly selectionChange = output<number[]>();

  protected readonly selectedIds = signal<Set<number>>(new Set());

  toggle(userId: number): void {
    this.selectedIds.update((current) => {
      const next = this.multi() ? new Set(current) : new Set<number>();
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });
    this.selectionChange.emit([...this.selectedIds()]);
  }
}
