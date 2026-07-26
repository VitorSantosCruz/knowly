import { Component, OnInit, effect, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Table } from 'primeng/table';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { Member, MemberService } from '../../core/member.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { MemberDetailPanelComponent } from './member-detail-panel.component';

type MembersError = 'network' | 'permission-denied' | null;

@Component({
  selector: 'app-members-page',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    MemberDetailPanelComponent,
    ButtonDirective,
    InputText,
    Table,
  ],
  template: `
    <div data-testid="members-page" class="page-shell max-w-3xl">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <form
          data-testid="add-member-form"
          class="enter-fluid mb-6 flex gap-2 rounded-2xl border border-ink-200/70 bg-white p-4 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
          (submit)="onAddMember($event)"
        >
          <input
            data-testid="add-member-email"
            type="email"
            name="email"
            pInputText
            [value]="newMemberEmail()"
            (input)="newMemberEmail.set($any($event.target).value)"
            class="flex-1"
          />
          <button type="submit" pButton>
            {{ 'members.add' | transloco }}
          </button>
        </form>

        <p-table
          data-testid="members-list"
          [value]="members()"
          styleClass="enter-fluid overflow-hidden rounded-2xl border border-ink-200/70 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:shadow-none"
        >
          <ng-template #body let-member>
            <tr>
              <td>
                <span
                  [attr.data-testid]="'select-member-' + member.membershipId"
                  (click)="selectedMembershipId.set(member.membershipId)"
                  class="cursor-pointer text-sm text-ink-800 dark:text-ink-100"
                >
                  {{ member.email }}
                </span>
              </td>
              <td class="text-right">
                <button
                  [attr.data-testid]="'remove-member-' + member.membershipId"
                  (click)="onRemoveMember(member.membershipId)"
                  pButton
                  text
                  severity="danger"
                >
                  {{ 'members.remove' | transloco }}
                </button>
              </td>
            </tr>
          </ng-template>
        </p-table>

        @if (selectedMembershipId(); as membershipId) {
          <div class="mt-6">
            <app-member-detail-panel
              [tenantId]="activeTenantService.activeTenantId()!"
              [membershipId]="membershipId"
            />
          </div>
        }
      }
    </div>
  `,
})
export class MembersPageComponent implements OnInit {
  protected readonly activeTenantService = inject(ActiveTenantService);
  private readonly memberService = inject(MemberService);

  protected readonly members = signal<Member[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<MembersError>(null);
  protected readonly newMemberEmail = signal('');
  protected readonly selectedMembershipId = signal<number | null>(null);

  private hasLoaded = false;

  constructor() {
    effect(() => {
      const tenantId = this.activeTenantService.activeTenantId();

      if (tenantId !== null && !this.hasLoaded) {
        this.hasLoaded = true;
        this.loadMembers(tenantId);
        this.memberService.listAccessGroups(tenantId).subscribe();
      }
    });
  }

  ngOnInit(): void {
    this.activeTenantService.fetch();
  }

  private loadMembers(tenantId: number): void {
    this.loading.set(true);
    this.error.set(null);

    this.memberService
      .list(tenantId)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of<Member[]>([]);
        }),
      )
      .subscribe((members) => {
        this.members.set(members);
        this.loading.set(false);
      });
  }

  protected onAddMember(event: Event): void {
    event.preventDefault();
    const tenantId = this.activeTenantService.activeTenantId();
    const email = this.newMemberEmail();

    if (tenantId === null || !email) {
      return;
    }

    this.memberService
      .add(tenantId, email, 'MEMBER')
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.newMemberEmail.set('');
          this.loadMembers(tenantId);
        }
      });
  }

  protected onRemoveMember(membershipId: number): void {
    const tenantId = this.activeTenantService.activeTenantId();

    if (tenantId === null) {
      return;
    }

    this.memberService
      .remove(tenantId, membershipId)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.members.update((members) => members.filter((m) => m.membershipId !== membershipId));
        }
      });
  }
}
