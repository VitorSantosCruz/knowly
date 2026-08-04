import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideSquarePen, LucideTrash2 } from '@lucide/angular';
import { EMPTY, Observable, catchError, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { Member, MemberService } from '../../core/member.service';
import { MandatoryProfileFields, ProfileFields } from '../../core/profile.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { SharedListComponent } from '../../shared/shared-list/shared-list.component';
import { SharedListColumn, SharedListRowAction } from '../../shared/shared-list/shared-list.model';
import {
  ProfileFieldsFormComponent,
  ProfileFieldsFormSubmission,
} from '../../shared/profile-fields-form.component';
import { MemberDetailPanelComponent } from './member-detail-panel.component';

type MembersError = 'network' | 'permission-denied' | null;

// Same empty starting point complete-profile-page.component.ts uses for the same form.
const EMPTY_FIELDS: ProfileFields = {
  fullName: '',
  taxId: '',
  countryCode: '',
  address: {
    addressLine1: '',
    addressLine2: '',
    city: '',
    stateRegion: '',
    postalCode: '',
    countryCode: '',
  },
  contacts: [],
};

@Component({
  selector: 'app-members-page',
  imports: [
    TranslocoPipe,
    ErrorStateComponent,
    NoAccessStateComponent,
    SharedListComponent,
    ProfileFieldsFormComponent,
    MemberDetailPanelComponent,
    ConfirmDialogComponent,
  ],
  template: `
    <div data-testid="members-page" class="page-shell">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        @if (!showAddProfileForm()) {
          <form
            data-testid="add-member-form"
            class="enter-fluid mb-6 flex gap-2 rounded-2xl border border-ink-200/70 bg-white p-4 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
            (submit)="onStartAddMember($event)"
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
        } @else {
          <div
            data-testid="add-member-profile-form"
            class="enter-fluid mb-6 rounded-2xl border border-ink-200/70 bg-white p-4 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
          >
            <p class="mb-3 text-sm text-ink-600 dark:text-ink-400">
              {{ 'members.addProfileIntro' | transloco: { email: newMemberEmail() } }}
            </p>
            @if (addError(); as addErrorMessage) {
              <p data-testid="add-member-error" class="mb-3 text-sm text-red-600 dark:text-red-400">
                {{ addErrorMessage | transloco }}
              </p>
            }
            <app-profile-fields-form
              [fields]="newMemberProfileFields()"
              [requireAllFields]="true"
              [disabled]="adding()"
              (submitted)="onAddMember($event)"
            />
            <button
              type="button"
              data-testid="add-member-cancel"
              [class]="secondaryButtonClass"
              [disabled]="adding()"
              (click)="onCancelAddMember()"
            >
              {{ 'common.cancel' | transloco }}
            </button>
          </div>
        }

        <app-shared-list
          data-testid="members-list"
          [title]="'members.title' | transloco"
          [rows]="members()"
          [columns]="columns"
          [rowActions]="rowActions"
          [rowId]="rowId"
          [emptyMessageKey]="'sharedList.empty.tenantMembers'"
        />

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
  protected readonly secondaryButtonClass = buttonClass('secondary');
  protected readonly members = signal<Member[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<MembersError>(null);
  protected readonly newMemberEmail = signal('');
  protected readonly selectedMembershipId = signal<number | null>(null);
  protected readonly pendingRemoval = signal<Member | null>(null);
  protected readonly removalRetryToken = signal(0);

  // mandatory-complete-profile (backend): adding a member requires a full
  // MandatoryProfileFieldsDto — this two-step flow collects it via the same
  // ProfileFieldsFormComponent complete-profile-page.component.ts already uses.
  protected readonly showAddProfileForm = signal(false);
  protected readonly newMemberProfileFields = signal<ProfileFields>(EMPTY_FIELDS);
  protected readonly adding = signal(false);
  protected readonly addError = signal<string | null>(null);

  protected readonly rowId = (row: Member): number => row.membershipId;

  protected readonly columns: SharedListColumn<Member>[] = [
    {
      key: 'email',
      headerKey: 'members.columns.email',
      sortable: true,
      render: (row) => ({
        type: 'identity',
        primary: row.email,
        initials: row.email.charAt(0).toUpperCase(),
      }),
    },
    {
      key: 'role',
      headerKey: 'members.columns.role',
      essential: false,
      render: (row) => ({
        type: 'pill',
        labelKey: `members.roles.${row.role}`,
        colorClass:
          row.role === 'MEMBER_ADMIN'
            ? 'bg-signal-100 text-signal-700 dark:bg-signal-900/40 dark:text-signal-300'
            : 'bg-ink-100 text-ink-700 dark:bg-ink-800 dark:text-ink-300',
      }),
    },
  ];

  protected readonly rowActions: SharedListRowAction<Member>[] = [
    {
      icon: LucideSquarePen,
      labelKey: 'sharedList.actions.edit',
      variant: 'secondary',
      onClick: (row) => this.selectedMembershipId.set(row.membershipId),
    },
    {
      icon: LucideTrash2,
      labelKey: 'sharedList.actions.delete',
      variant: 'danger',
      onClick: (row) => this.onRemoveMember(row.membershipId),
    },
  ];

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

  protected onStartAddMember(event: Event): void {
    event.preventDefault();

    if (!this.newMemberEmail()) {
      return;
    }

    this.addError.set(null);
    this.newMemberProfileFields.set(EMPTY_FIELDS);
    this.showAddProfileForm.set(true);
  }

  protected onCancelAddMember(): void {
    this.showAddProfileForm.set(false);
    this.newMemberEmail.set('');
    this.addError.set(null);
  }

  protected onAddMember({ fields }: ProfileFieldsFormSubmission): void {
    if (this.adding()) {
      return;
    }

    const tenantId = this.activeTenantService.activeTenantId();
    const email = this.newMemberEmail();

    if (tenantId === null) {
      return;
    }

    this.newMemberProfileFields.set(fields);
    this.addError.set(null);
    this.adding.set(true);

    const profile: MandatoryProfileFields = {
      ...fields,
      contacts: fields.contacts.map((contact) => ({
        type: contact.type,
        value: contact.value,
        label: contact.label,
        isPrimary: contact.isPrimary,
      })),
    };

    this.memberService
      .add(tenantId, email, 'MEMBER', profile)
      .pipe(
        catchError((err) => {
          this.adding.set(false);

          if (err.status === 403) {
            this.error.set('permission-denied');
          } else if (err.status === 400) {
            this.addError.set('members.addProfileError');
          } else {
            this.error.set('network');
          }

          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.adding.set(false);
          this.showAddProfileForm.set(false);
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
