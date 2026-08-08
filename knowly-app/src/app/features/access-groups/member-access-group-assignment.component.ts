import { Component, computed, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { buttonClass } from '../../shared/button-classes';
import { AccessGroup } from '../../core/member.service';

/**
 * Presentational checkbox-matrix reused from both
 * `TenantAccessGroupManagementPageComponent` (REQ-9, member ⇒ groups direction) and, in a
 * future SPEC, `member-detail-panel.component.ts`'s own bulk case — see PLAN.md's rationale
 * for keeping this its own standalone component instead of screen-local. Owns no HTTP calls:
 * the parent decides single-call-vs-batch based on how many ids come back on `submitted`.
 */
@Component({
  selector: 'app-member-access-group-assignment',
  imports: [TranslocoPipe],
  template: `
    <div data-testid="member-access-group-assignment">
      <ul class="mb-3 flex flex-col gap-1">
        @for (group of allGroups(); track group.id) {
          <li class="flex items-center gap-2 text-sm text-ink-700 dark:text-ink-300">
            <input
              type="checkbox"
              [id]="'group-checkbox-' + group.id"
              [attr.data-testid]="'group-checkbox-' + group.id"
              [checked]="selectedIds().has(group.id)"
              (change)="onToggle(group.id)"
              class="h-4 w-4 rounded border-ink-300 text-signal-600 focus:ring-signal-500 dark:border-ink-700"
            />
            <label [for]="'group-checkbox-' + group.id">{{ group.name }}</label>
          </li>
        }
      </ul>
      <button
        type="button"
        data-testid="group-assignment-submit"
        [class]="primaryButtonClass"
        (click)="onSubmit()"
      >
        {{ 'common.confirm' | transloco }}
      </button>
    </div>
  `,
})
export class MemberAccessGroupAssignmentComponent {
  readonly allGroups = input.required<AccessGroup[]>();
  readonly assignedGroupIds = input.required<Set<number>>();
  readonly submitted = output<number[]>();

  protected readonly primaryButtonClass = buttonClass('primary');

  private readonly toggled = signal<Set<number> | null>(null);

  // Starts from assignedGroupIds() on first render, then diverges as the caller toggles boxes —
  // `toggled` is null until the first toggle, so this stays in sync if the parent swaps
  // `assignedGroupIds` (e.g. selecting a different candidate) before any toggle happens.
  protected readonly selectedIds = computed(() => this.toggled() ?? this.assignedGroupIds());

  protected onToggle(groupId: number): void {
    const next = new Set(this.selectedIds());

    if (next.has(groupId)) {
      next.delete(groupId);
    } else {
      next.add(groupId);
    }

    this.toggled.set(next);
  }

  protected onSubmit(): void {
    this.submitted.emit([...this.selectedIds()]);
  }
}
