import { Component, OnInit, effect, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { Member, MemberService } from '../../core/member.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { MemberDetailPanelComponent } from './member-detail-panel.component';

type MembersError = 'network' | 'permission-denied' | null;

@Component({
  selector: 'app-members-page',
  imports: [TranslocoPipe, ErrorStateComponent, NoAccessStateComponent, MemberDetailPanelComponent],
  template: `
    <div data-testid="members-page" class="mx-auto max-w-3xl p-6">
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
            [value]="newMemberEmail()"
            (input)="newMemberEmail.set($any($event.target).value)"
            class="flex-1 rounded-xl border border-ink-300/70 bg-white px-4 py-2 text-sm text-ink-900 shadow-sm transition-shadow duration-fast ease-fluid focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
          />
          <button
            type="submit"
            class="rounded-xl bg-ink-800 px-4 py-2 text-sm font-semibold text-white shadow-sm shadow-ink-900/20 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-signal-600 hover:shadow-md active:translate-y-0 active:scale-[0.98] active:bg-signal-700 dark:bg-ink-600 dark:hover:bg-signal-500"
          >
            {{ 'members.add' | transloco }}
          </button>
        </form>

        <ul
          data-testid="members-list"
          class="enter-fluid overflow-hidden rounded-2xl border border-ink-200/70 bg-white shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
        >
          @for (member of members(); track member.membershipId) {
            <li
              class="flex items-center justify-between border-b border-ink-100 px-4 py-3 transition-colors duration-fast ease-fluid last:border-b-0 hover:bg-ink-50 dark:border-ink-800 dark:hover:bg-ink-800/50"
            >
              <span
                [attr.data-testid]="'select-member-' + member.membershipId"
                (click)="selectedMembershipId.set(member.membershipId)"
                class="cursor-pointer text-sm text-ink-800 dark:text-ink-100"
              >
                {{ member.email }}
              </span>
              <button
                [attr.data-testid]="'remove-member-' + member.membershipId"
                (click)="onRemoveMember(member.membershipId)"
                class="text-sm text-red-600 transition-colors duration-fast ease-fluid hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
              >
                {{ 'members.remove' | transloco }}
              </button>
            </li>
          }
        </ul>

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
