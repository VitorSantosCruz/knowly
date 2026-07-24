import { Component, OnChanges, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ALL_PERMISSIONS, Permission } from '../../core/permission';
import { AccessGroup, MemberDetail, MemberService } from '../../core/member.service';

@Component({
  selector: 'app-member-detail-panel',
  imports: [TranslocoPipe],
  template: `
    @if (detail(); as detail) {
      <div data-testid="member-detail-panel" class="rounded-2xl border border-slate-200 p-4">
        <h2 class="mb-2 font-semibold">{{ detail.email }}</h2>

        <section data-testid="direct-permissions" class="mb-4">
          <h3 class="mb-1 text-sm font-medium">{{ 'members.directPermissions' | transloco }}</h3>
          @for (permission of allPermissions; track permission) {
            <label class="mr-3 inline-flex items-center gap-1 text-sm">
              <input
                type="checkbox"
                [attr.data-testid]="'permission-toggle-' + permission"
                [checked]="detail.directPermissions.includes(permission)"
                (click)="
                  onTogglePermission(permission, detail.directPermissions.includes(permission))
                "
              />
              {{ permission }}
            </label>
          }
        </section>

        <section data-testid="access-groups" class="mb-4">
          <h3 class="mb-1 text-sm font-medium">{{ 'members.accessGroups' | transloco }}</h3>
          <ul>
            @for (group of detail.accessGroups; track group.id) {
              <li class="flex items-center justify-between text-sm">
                {{ group.name }}
                <button
                  [attr.data-testid]="'unassign-access-group-' + group.id"
                  (click)="onUnassignAccessGroup(group.id)"
                >
                  {{ 'members.unassign' | transloco }}
                </button>
              </li>
            }
          </ul>

          <ul>
            @for (group of assignableAccessGroups(detail); track group.id) {
              <li class="text-sm">
                {{ group.name }}
                <button
                  [attr.data-testid]="'assign-access-group-' + group.id"
                  (click)="onAssignAccessGroup(group.id)"
                >
                  {{ 'members.assign' | transloco }}
                </button>
              </li>
            }
          </ul>

          <form
            data-testid="new-access-group-form"
            class="mt-2 flex gap-2"
            (submit)="onCreateAccessGroup($event)"
          >
            <input
              data-testid="new-access-group-name"
              type="text"
              [value]="newAccessGroupName()"
              (input)="newAccessGroupName.set($any($event.target).value)"
              class="rounded border border-slate-300 px-2 py-1 text-sm"
            />
            <button type="submit" class="text-sm">{{ 'members.createGroup' | transloco }}</button>
          </form>
        </section>

        <section data-testid="effective-permissions">
          <h3 class="mb-1 text-sm font-medium">{{ 'members.effectivePermissions' | transloco }}</h3>
          <p class="text-sm">{{ detail.effectivePermissions.join(', ') }}</p>
        </section>
      </div>
    }
  `,
})
export class MemberDetailPanelComponent implements OnChanges {
  private readonly memberService = inject(MemberService);

  readonly tenantId = input.required<number>();
  readonly membershipId = input.required<number>();

  protected readonly detail = signal<MemberDetail | null>(null);
  protected readonly availableAccessGroups = signal<AccessGroup[]>([]);
  protected readonly newAccessGroupName = signal('');
  protected readonly allPermissions = ALL_PERMISSIONS;

  ngOnChanges(): void {
    this.loadDetail();
    this.loadAccessGroups();
  }

  protected assignableAccessGroups(detail: MemberDetail): AccessGroup[] {
    const assignedIds = new Set(detail.accessGroups.map((group) => group.id));
    return this.availableAccessGroups().filter((group) => !assignedIds.has(group.id));
  }

  private loadDetail(): void {
    this.memberService
      .getDetail(this.tenantId(), this.membershipId())
      .subscribe((detail) => this.detail.set(detail));
  }

  private loadAccessGroups(): void {
    this.memberService
      .listAccessGroups(this.tenantId())
      .subscribe((groups) => this.availableAccessGroups.set(groups));
  }

  protected onTogglePermission(permission: Permission, isGranted: boolean): void {
    const request$ = isGranted
      ? this.memberService.revokePermission(this.tenantId(), this.membershipId(), permission)
      : this.memberService.grantPermission(this.tenantId(), this.membershipId(), permission);

    request$.subscribe(() => this.loadDetail());
  }

  protected onAssignAccessGroup(accessGroupId: number): void {
    this.memberService
      .assignAccessGroup(this.tenantId(), this.membershipId(), accessGroupId)
      .subscribe(() => this.loadDetail());
  }

  protected onUnassignAccessGroup(accessGroupId: number): void {
    this.memberService
      .unassignAccessGroup(this.tenantId(), this.membershipId(), accessGroupId)
      .subscribe(() => this.loadDetail());
  }

  protected onCreateAccessGroup(event: Event): void {
    event.preventDefault();
    const name = this.newAccessGroupName();

    if (!name) {
      return;
    }

    this.memberService.createAccessGroup(this.tenantId(), name).subscribe(() => {
      this.newAccessGroupName.set('');
      this.loadAccessGroups();
    });
  }
}
