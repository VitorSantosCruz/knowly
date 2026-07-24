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
    <div data-testid="members-page" class="p-6">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-slate-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <form data-testid="add-member-form" class="mb-4 flex gap-2" (submit)="onAddMember($event)">
          <input
            data-testid="add-member-email"
            type="email"
            name="email"
            [value]="newMemberEmail()"
            (input)="newMemberEmail.set($any($event.target).value)"
            class="rounded border border-slate-300 px-2 py-1"
          />
          <button type="submit" class="rounded bg-indigo-600 px-3 py-1 text-white">
            {{ 'members.add' | transloco }}
          </button>
        </form>

        <ul data-testid="members-list">
          @for (member of members(); track member.membershipId) {
            <li class="flex items-center justify-between border-b border-slate-200 py-2">
              <span
                [attr.data-testid]="'select-member-' + member.membershipId"
                (click)="selectedMembershipId.set(member.membershipId)"
                class="cursor-pointer"
              >
                {{ member.email }}
              </span>
              <button
                [attr.data-testid]="'remove-member-' + member.membershipId"
                (click)="onRemoveMember(member.membershipId)"
                class="text-sm text-red-600"
              >
                {{ 'members.remove' | transloco }}
              </button>
            </li>
          }
        </ul>

        @if (selectedMembershipId(); as membershipId) {
          <app-member-detail-panel
            [tenantId]="activeTenantService.activeTenantId()!"
            [membershipId]="membershipId"
          />
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

    this.memberService.add(tenantId, email, 'MEMBER').subscribe(() => {
      this.newMemberEmail.set('');
      this.loadMembers(tenantId);
    });
  }

  protected onRemoveMember(membershipId: number): void {
    const tenantId = this.activeTenantService.activeTenantId();

    if (tenantId === null) {
      return;
    }

    this.memberService.remove(tenantId, membershipId).subscribe(() => {
      this.members.update((members) => members.filter((m) => m.membershipId !== membershipId));
    });
  }
}
