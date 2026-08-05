import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { AuditEvent, StaffUserService } from '../../core/staff-user.service';
import { formatAuditTimestamp } from '../../shared/audit-timestamp';
import { translateAuditAction } from '../../shared/audit-trail-labels';
import { SharedListComponent } from '../../shared/shared-list/shared-list.component';
import { SharedListColumn, SharedListError } from '../../shared/shared-list/shared-list.model';

const PAGE_SIZE = 20;

/**
 * Route: `/staff/users/:userId/audit` — replaces `staff-user-detail-panel.component.ts`'s
 * former embedded (unpaginated) audit-trail table (PLAN.md's "Components and routes"). No row
 * actions: this is a read-only history view.
 */
@Component({
  selector: 'app-staff-user-audit-page',
  imports: [TranslocoPipe, SharedListComponent],
  template: `
    <div data-testid="staff-user-audit-page" class="page-shell">
      <h1
        class="mb-6 font-display text-2xl font-semibold tracking-tight text-ink-900 dark:text-white"
      >
        {{ 'staffDirectory.auditTrail.title' | transloco }}
      </h1>
      <app-shared-list
        [title]="'staffDirectory.auditTrail.title' | transloco"
        [rows]="events()"
        [columns]="columns"
        [rowId]="rowId"
        [loading]="loading()"
        [error]="error()"
        [serverPagination]="serverPagination()"
        emptyMessageKey="staffDirectory.auditTrail.noHistory"
        (pageChange)="onPageChange($event)"
      />
    </div>
  `,
})
export class StaffUserAuditPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly staffUserService = inject(StaffUserService);
  private readonly transloco = inject(TranslocoService);

  private userId: number | null = null;

  protected readonly events = signal<AuditEvent[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<SharedListError>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);

  protected readonly rowId = (row: AuditEvent): number => this.events().indexOf(row);

  protected readonly serverPagination = computed(() => ({
    page: this.page(),
    totalPages: this.totalPages(),
    totalElements: this.totalElements(),
  }));

  protected readonly columns: SharedListColumn<AuditEvent>[] = [
    {
      key: 'occurredAt',
      headerKey: 'staffDirectory.auditTrail.occurredAt',
      render: (row) => ({
        type: 'text',
        value: `${formatAuditTimestamp(row.occurredAt)} — ${translateAuditAction(row.action, this.transloco)}`,
      }),
    },
  ];

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const userId = Number(params.get('userId'));
      this.userId = userId;
      this.page.set(0);
      this.loadPage();
    });
  }

  protected onPageChange(delta: -1 | 1): void {
    this.page.set(this.page() + delta);
    this.loadPage();
  }

  private loadPage(): void {
    if (this.userId === null) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.staffUserService
      .getAuditTrail(this.userId, this.page(), PAGE_SIZE)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of(null);
        }),
      )
      .subscribe((response) => {
        this.loading.set(false);

        if (response === null) {
          return;
        }

        this.events.set(response.content);
        this.totalPages.set(response.totalPages);
        this.totalElements.set(response.totalElements);
      });
  }
}
