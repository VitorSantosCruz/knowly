import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EMPTY, Observable, catchError, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { Member, MemberService } from '../../core/member.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { MemberDetailPanelComponent } from './member-detail-panel.component';

type MembersError = 'network' | 'permission-denied' | null;

@Component({
  selector: 'app-members-page',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    MemberDetailPanelComponent,
    ConfirmDialogComponent,
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
            [value]="newMemberEmail()"
            (input)="newMemberEmail.set($any($event.target).value)"
            class="flex-1 rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white"
          />
          <button type="submit" [class]="addButtonClass">
            {{ 'members.add' | transloco }}
          </button>
        </form>

        <table
          data-testid="members-list"
          class="enter-fluid w-full overflow-hidden rounded-2xl border border-ink-200/70 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:shadow-none"
        >
          <tbody>
            @for (member of members(); track member.membershipId) {
              <tr>
                <td>
                  <span
                    [attr.data-testid]="'select-member-' + member.membershipId"
                    role="button"
                    tabindex="0"
                    (click)="selectedMembershipId.set(member.membershipId)"
                    (keydown.enter)="selectedMembershipId.set(member.membershipId)"
                    class="cursor-pointer text-sm text-ink-800 dark:text-ink-100"
                  >
                    {{ member.email }}
                  </span>
                </td>
                <td class="text-right">
                  <button
                    [attr.data-testid]="'remove-member-' + member.membershipId"
                    (click)="onRemoveMember(member.membershipId)"
                    [class]="removeButtonClass"
                  >
                    {{ 'members.remove' | transloco }}
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>

        @if (selectedMembershipId(); as membershipId) {
          <div class="mt-6">
            <app-member-detail-panel
              [tenantId]="activeTenantService.activeTenantId()!"
              [membershipId]="membershipId"
              [viewerIsMemberAdminOfThisTenant]="viewerIsMemberAdminOfThisTenant()"
            />
          </div>
        }

        @if (pendingRemoval(); as memberToRemove) {
          <app-confirm-dialog
            [open]="true"
            [message]="'members.confirmRemove' | transloco: { email: memberToRemove.email }"
            [fetchToken]="removalTokenFetcher(memberToRemove.membershipId)"
            [retryToken]="removalRetryToken()"
            (confirm)="confirmRemoval($event)"
            (dismissed)="cancelRemoval()"
          />
        }
      }
    </div>
  `,
})
export class MembersPageComponent implements OnInit {
  protected readonly activeTenantService = inject(ActiveTenantService);
  private readonly memberService = inject(MemberService);

  protected readonly addButtonClass = buttonClass('primary');
  protected readonly removeButtonClass = buttonClass('danger', { ghost: true });
  protected readonly members = signal<Member[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<MembersError>(null);
  protected readonly newMemberEmail = signal('');
  protected readonly selectedMembershipId = signal<number | null>(null);
  protected readonly pendingRemoval = signal<Member | null>(null);
  protected readonly removalRetryToken = signal(0);

  protected readonly viewerIsMemberAdminOfThisTenant = computed(
    () => this.activeTenantService.activeTenantRole() === 'MEMBER_ADMIN',
  );

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
    const member = this.members().find((m) => m.membershipId === membershipId);

    if (member === undefined) {
      return;
    }

    this.pendingRemoval.set(member);
  }

  protected removalTokenFetcher(membershipId: number): () => Observable<string> {
    return () => {
      const tenantId = this.activeTenantService.activeTenantId();
      return this.memberService.generateRemovalToken(tenantId ?? -1, membershipId);
    };
  }

  protected confirmRemoval(word: string): void {
    const tenantId = this.activeTenantService.activeTenantId();
    const member = this.pendingRemoval();

    if (tenantId === null || member === null) {
      return;
    }

    this.memberService
      .remove(tenantId, member.membershipId, word)
      .pipe(
        catchError((err) => {
          if (err.status === 400) {
            this.removalRetryToken.update((n) => n + 1);
          } else {
            this.pendingRemoval.set(null);
            this.removalRetryToken.set(0);
            this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          }
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.pendingRemoval.set(null);
        this.removalRetryToken.set(0);
        this.members.update((members) =>
          members.filter((m) => m.membershipId !== member.membershipId),
        );
      });
  }

  protected cancelRemoval(): void {
    this.pendingRemoval.set(null);
    this.removalRetryToken.set(0);
  }
}
