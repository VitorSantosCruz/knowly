import { Component, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideSquarePen, LucideTrash, LucideUser, LucideUserX } from '@lucide/angular';
import { EMPTY, Observable, catchError, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { PermissionsService } from '../../core/permissions.service';
import { Member, MemberService } from '../../core/member.service';
import { MandatoryProfileFields, ProfileFields, ProfileService } from '../../core/profile.service';
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
          [rowActions]="rowActions()"
          [rowId]="rowId"
          [emptyMessageKey]="'sharedList.empty.tenantMembers'"
        />

        @if (selectedMembershipId(); as membershipId) {
          <div class="mt-6">
            <app-member-detail-panel
              #memberDetailPanel
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

        @if (pendingHardDelete(); as memberToHardDelete) {
          <app-confirm-dialog
            [open]="true"
            [message]="'members.confirmDelete' | transloco: { email: memberToHardDelete.email }"
            [fetchToken]="hardDeleteTokenFetcher(memberToHardDelete.membershipId)"
            [retryToken]="hardDeleteRetryToken()"
            (confirm)="confirmHardDelete($event)"
            (dismissed)="cancelHardDelete()"
          />
        }
      }
    </div>
  `,
})
export class MembersPageComponent implements OnInit {
  protected readonly activeTenantService = inject(ActiveTenantService);
  private readonly memberService = inject(MemberService);
  private readonly profileService = inject(ProfileService);
  private readonly permissionsService = inject(PermissionsService);
  private readonly router = inject(Router);

  protected readonly addButtonClass = buttonClass('primary');
  protected readonly secondaryButtonClass = buttonClass('secondary');
  protected readonly members = signal<Member[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<MembersError>(null);
  protected readonly newMemberEmail = signal('');
  protected readonly selectedMembershipId = signal<number | null>(null);
  protected readonly pendingRemoval = signal<Member | null>(null);
  protected readonly removalRetryToken = signal(0);
  protected readonly pendingHardDelete = signal<Member | null>(null);
  protected readonly hardDeleteRetryToken = signal(0);

  // REQ-10: sourced once, kept page-local per PLAN's "second call site for the same
  // one-shot value, no caching layer needed" judgment call.
  protected readonly ownUserId = signal<number | null>(null);
  private readonly memberDetailPanel = viewChild<MemberDetailPanelComponent>('memberDetailPanel');
  private pendingEditMode = false;

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

  // REQ-10 (own-row swap, appsec review 2026-08-05): the viewer's own row omits edit
  // (self-edit-by-request is never allowed, identity-profile-model-v2's REQ-11) *and*
  // delete (no legitimate self-removal UI path exists either; the backend already
  // rejects it independently — this is defense-in-depth UX clarity, not the actual
  // authorization boundary), replaced by a "my profile" action navigating to /profile.
  // `SharedListRowAction`'s per-row `hidden(row)` predicate (extended alongside this
  // task, mirroring the existing per-row `disabled(row)` shape) is what makes a
  // genuinely per-row (not all-or-nothing) omission possible from one flat array.
  protected readonly rowActions = computed<SharedListRowAction<Member>[]>(() => {
    const ownUserId = this.ownUserId();
    const isOwnRow = (row: Member) => row.userId === ownUserId;

    return [
      {
        icon: LucideSquarePen,
        labelKey: 'sharedList.actions.edit',
        variant: 'secondary',
        hidden: isOwnRow,
        onClick: (row: Member) => this.openInEditMode(row.membershipId),
      },
      {
        // Deliberately 'secondary', not 'danger' -- this is a reversible action (the
        // member can be re-invited), unlike the irreversible hard-delete below. A user
        // reported the two actions read as "two delete buttons" side by side when both
        // were LucideTrash-red; distinct icon, label, and variant now separate them.
        icon: LucideTrash,
        labelKey: 'members.removeFromTenant',
        variant: 'secondary',
        hidden: isOwnRow,
        onClick: (row: Member) => this.onRemoveMember(row.membershipId),
      },
      {
        icon: LucideUser,
        labelKey: 'sharedList.actions.myProfile',
        variant: 'secondary',
        hidden: (row: Member) => !isOwnRow(row),
        onClick: () => this.router.navigateByUrl('/profile'),
      },
      {
        // Hard-delete (irreversible account deletion) — distinct from the soft "remove
        // from tenant" action above, and distinct icon on purpose (LucideUserX, not
        // LucideTrash) so the two aren't visually confusable on the same row. This was
        // previously reachable only via a button at the bottom of
        // member-detail-panel.component.ts (removed in the design-system-consistency-pass
        // migration to list-level actions) — restored here as a row action using the same
        // gate the panel used (viewerCanHardDelete). Unlike the old panel, this list has
        // no per-row `isLastAdminOfType` (that field only exists on the full
        // `MemberDetail` fetch, not the lightweight `Member` row) so the button is never
        // pre-emptively disabled for that case — the backend's existing
        // `LastAdminRemainingException` check still rejects it, surfaced as a generic
        // error rather than a disabled button; a deliberate, documented tradeoff.
        icon: LucideUserX,
        labelKey: 'members.delete',
        variant: 'danger',
        hidden: (row: Member) => isOwnRow(row) || !this.viewerCanHardDelete(row),
        onClick: (row: Member) => this.onHardDeleteMember(row),
      },
    ];
  });

  protected readonly viewerIsMemberAdminOfThisTenant = computed(
    () => this.activeTenantService.activeTenantRole() === 'MEMBER_ADMIN',
  );

  // Mirrors member-detail-panel.component.ts's removed `viewerCanDelete`: an admin-tier
  // target's hard-delete is only offered to a viewer who is themselves that tenant's
  // MEMBER_ADMIN; a plain-MEMBER target is also reachable via TENANT_MEMBER_MANAGE.
  protected viewerCanHardDelete(member: Member): boolean {
    if (member.role === 'MEMBER_ADMIN') {
      return this.viewerIsMemberAdminOfThisTenant();
    }

    return (
      this.viewerIsMemberAdminOfThisTenant() || this.permissionsService.has('TENANT_MEMBER_MANAGE')
    );
  }

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

    // REQ-6: the row's edit action must open the panel already in edit mode. The panel
    // only mounts once `selectedMembershipId()` is set (it wasn't necessarily open
    // before this click), so the `openInEditMode()` call on the panel instance is
    // deferred to this effect, which re-runs once the panel's viewChild resolves after
    // the same change-detection pass that rendered it.
    effect(() => {
      const panel = this.memberDetailPanel();

      if (panel !== undefined && this.pendingEditMode) {
        this.pendingEditMode = false;
        panel.openInEditMode();
      }
    });
  }

  ngOnInit(): void {
    this.activeTenantService.fetch();
    this.loadOwnUserId();
  }

  private loadOwnUserId(): void {
    this.profileService
      .getOwnProfile()
      .pipe(catchError(() => of(null)))
      .subscribe((profile) => {
        if (profile !== null) {
          this.ownUserId.set(profile.userId);
        }
      });
  }

  protected openInEditMode(membershipId: number): void {
    const panel = this.memberDetailPanel();
    this.selectedMembershipId.set(membershipId);

    if (panel !== undefined) {
      // Panel already mounted (from a previous selection) — its own `ngOnChanges` will
      // re-fetch for the new `membershipId`, but the trigger still needs bumping here
      // since the effect below only reacts to the panel's viewChild *appearing*, not to
      // `membershipId` changing on an already-mounted instance.
      panel.openInEditMode();
    } else {
      this.pendingEditMode = true;
    }
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
          } else if (err.status === 409 && err.error?.code === 'TAX_ID_ALREADY_EXISTS') {
            this.addError.set('members.addProfileTaxIdConflict');
          } else if (err.status === 400 || err.status === 409) {
            this.addError.set('members.addProfileError');
          } else {
            this.error.set('network');
          }

          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.adding.set(false);
        this.showAddProfileForm.set(false);
        this.newMemberEmail.set('');
        this.loadMembers(tenantId);
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

  protected onHardDeleteMember(member: Member): void {
    this.pendingHardDelete.set(member);
  }

  protected hardDeleteTokenFetcher(membershipId: number): () => Observable<string> {
    return () => {
      const tenantId = this.activeTenantService.activeTenantId();
      return this.memberService.generateHardDeleteToken(tenantId ?? -1, membershipId);
    };
  }

  protected confirmHardDelete(word: string): void {
    const tenantId = this.activeTenantService.activeTenantId();
    const member = this.pendingHardDelete();

    if (tenantId === null || member === null) {
      return;
    }

    this.memberService
      .hardDelete(tenantId, member.membershipId, word)
      .pipe(
        catchError((err) => {
          if (err.status === 400) {
            this.hardDeleteRetryToken.update((n) => n + 1);
          } else {
            this.pendingHardDelete.set(null);
            this.hardDeleteRetryToken.set(0);
            this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          }
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.pendingHardDelete.set(null);
        this.hardDeleteRetryToken.set(0);
        this.members.update((members) =>
          members.filter((m) => m.membershipId !== member.membershipId),
        );
      });
  }

  protected cancelHardDelete(): void {
    this.pendingHardDelete.set(null);
    this.hardDeleteRetryToken.set(0);
  }

  protected cancelRemoval(): void {
    this.pendingRemoval.set(null);
    this.removalRetryToken.set(0);
  }
}
