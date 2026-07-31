import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { interval } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { ChatService } from '../../core/chat.service';
import { STAFF_SUPPORT_HANDLE, SUPPORT_CHANNEL_VIEW } from '../../core/chat-permission';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { PermissionsService } from '../../core/permissions.service';
import { ProfileService } from '../../core/profile.service';
import { SupportService } from '../../core/support.service';
import { MemberSupportBrowseComponent } from './member-support-browse.component';
import { MemberSupportChannelComponent } from './member-support-channel.component';
import { StaffSupportChannelComponent } from './staff-support-channel.component';
import { StaffSupportInboxComponent } from './staff-support-inbox.component';

const POLL_INTERVAL_MS = 5000;

/**
 * Route: `/support`, `/support/:channelId`. Dispatches to one of three sub-views by
 * permission — no route guard, per PLAN.md's rationale (the same `/support` route must
 * render three different things depending on the viewer's own permissions, checked here via
 * `GET /api/staff/permissions` + `GET /api/tenants/permissions`, mirroring `staffGuard`'s
 * "check the actual permission" fix rather than gating the whole route on one guard):
 *
 * 1. Holds `STAFF_SUPPORT_HANDLE` → the staff inbox + claimed-channel view.
 * 2. Else holds `SUPPORT_CHANNEL_VIEW` → member-browse alongside their own channel.
 * 3. Else → their own channel only.
 *
 * `:channelId` (a `ChatConversation`/support-channel id, from `TicketSummary.supportChannelId`)
 * is resolved to `{tenantId, memberUserId}` via `ChatService.openConversation(channelId)` —
 * `SupportTicketDto` carries neither field, but the channel's own participant list does (the
 * member is the channel's only formal `ChatParticipant` — see `SupportTicketService`), so this
 * reuses the peer-chat detail fetch as the bridge rather than inventing a redundant endpoint.
 */
@Component({
  selector: 'app-support-page',
  imports: [
    TranslocoPipe,
    FormsModule,
    StaffSupportInboxComponent,
    StaffSupportChannelComponent,
    MemberSupportChannelComponent,
    MemberSupportBrowseComponent,
  ],
  template: `
    <div data-testid="support-page" class="page-shell flex flex-col gap-6">
      @if (isStaffHandler()) {
        <div class="grid gap-6 md:grid-cols-[320px_1fr]">
          <app-staff-support-inbox />
          @if (staffChannel(); as staffChannel) {
            <app-staff-support-channel
              [tenantId]="staffChannel.tenantId"
              [memberUserId]="staffChannel.memberUserId"
              [currentUserId]="currentUserId()!"
            />
          }
        </div>
      } @else {
        <div class="flex flex-col gap-6">
          @if (canBrowseSupport()) {
            <div class="flex flex-col gap-3">
              <label class="text-sm text-ink-600 dark:text-ink-400" for="browse-member-id">
                {{ 'support.browse.title' | transloco }}
              </label>
              <input
                id="browse-member-id"
                type="number"
                data-testid="browse-member-id-input"
                [(ngModel)]="browseMemberUserId"
                name="browseMemberUserId"
                class="w-32 rounded-lg border border-ink-200/70 px-2 py-1 text-sm dark:border-ink-800/70"
              />
              @if (browseMemberUserId !== null && activeTenantId(); as tenantId) {
                <app-member-support-browse
                  [tenantId]="tenantId"
                  [memberUserId]="browseMemberUserId!"
                />
              }
            </div>
          }

          @if (activeTenantId(); as tenantId) {
            <app-member-support-channel [tenantId]="tenantId" [memberUserId]="currentUserId()!" />
          } @else if (activeTenantService.activeTenantResolved()) {
            <p data-testid="support-empty-state" class="text-sm text-ink-500 dark:text-ink-400">
              {{ 'support.member.emptyState' | transloco }}
            </p>
          }
        </div>
      }
    </div>
  `,
})
export class SupportPageComponent implements OnInit {
  protected readonly activeTenantService = inject(ActiveTenantService);
  private readonly chatService = inject(ChatService);
  private readonly globalPermissionsService = inject(GlobalPermissionsService);
  private readonly permissionsService = inject(PermissionsService);
  private readonly profileService = inject(ProfileService);
  private readonly supportService = inject(SupportService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  protected readonly currentUserId = signal<number | null>(null);
  protected readonly routeChannelId = signal<number | null>(null);
  protected browseMemberUserId: number | null = null;

  private readonly resolvedChannelIds = new Set<number>();

  protected readonly activeTenantId = computed(() => this.activeTenantService.activeTenantId());

  protected readonly isStaffHandler = computed(() =>
    this.globalPermissionsService.has(STAFF_SUPPORT_HANDLE),
  );

  protected readonly canBrowseSupport = computed(() =>
    this.permissionsService.has(SUPPORT_CHANNEL_VIEW),
  );

  /**
   * The `ChatConversation`/support-channel id to resolve `{tenantId, memberUserId}` for —
   * either an explicit `:channelId` route param (deep link), or, when absent, the ticket
   * `StaffSupportInboxComponent.claim()` just navigated here for (it navigates to plain
   * `/support`, relying on `SupportService.activeTicket()` rather than a route param — see
   * that component).
   */
  private readonly effectiveChannelId = computed(
    () => this.routeChannelId() ?? this.supportService.activeTicket()?.supportChannelId ?? null,
  );

  /** Resolved from `effectiveChannelId` via `ChatService.openConversation` — see class doc comment. */
  protected readonly staffChannel = computed(() => {
    const id = this.effectiveChannelId();
    if (id === null) {
      return null;
    }
    const detail = this.chatService.details().get(id);
    if (!detail || detail.participantUserIds.length === 0 || detail.tenantId === null) {
      return null;
    }
    return { tenantId: detail.tenantId, memberUserId: detail.participantUserIds[0] };
  });

  constructor() {
    effect(() => {
      const id = this.effectiveChannelId();
      if (id !== null && !this.resolvedChannelIds.has(id)) {
        this.resolvedChannelIds.add(id);
        this.chatService.openConversation(id);
      }
    });
  }

  ngOnInit(): void {
    this.globalPermissionsService.fetch();
    this.permissionsService.fetch();
    this.activeTenantService.fetch();

    this.profileService
      .getOwnProfile()
      .subscribe((profile) => this.currentUserId.set(profile.userId));

    this.route.paramMap.subscribe((params) => {
      const raw = params.get('channelId');
      this.routeChannelId.set(raw === null ? null : Number(raw));
    });

    interval(POLL_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.document.visibilityState !== 'visible') {
          return;
        }
        const staffChannel = this.staffChannel();
        if (staffChannel) {
          this.supportService.pollNewMessages(staffChannel.tenantId, staffChannel.memberUserId);
          return;
        }
        const tenantId = this.activeTenantId();
        const memberUserId = this.currentUserId();
        if (tenantId !== null && memberUserId !== null && !this.isStaffHandler()) {
          this.supportService.pollNewMessages(tenantId, memberUserId);
        }
      });
  }
}
