import { Routes } from '@angular/router';
import { LoginPageComponent } from './features/login/login-page.component';
import { WelcomePageComponent } from './features/welcome/welcome-page.component';
import { DashboardWrapperPageComponent } from './features/dashboard/dashboard-wrapper-page.component';
import { UserManagementPageComponent } from './features/user-management/user-management-page.component';
import { ConversationsPageComponent } from './features/conversations/conversations-page.component';
import { ArticlesPageComponent } from './features/articles/articles-page.component';
import { SelectTenantPageComponent } from './features/select-tenant/select-tenant-page.component';
import { TenantCreatePageComponent } from './features/tenant-create/tenant-create-page.component';
import { tenantSelectionGuard } from './core/tenant-selection.guard';
import { staffGuard } from './core/staff.guard';
import { accessGroupManagementGuard } from './core/access-group-management.guard';
import { AccessGroupManagementPageComponent } from './features/access-groups/access-group-management-page.component';
import { rootRedirectGuard } from './core/root-redirect.guard';
import { RootRedirectPlaceholderComponent } from './core/root-redirect-placeholder.component';
import { OwnProfilePageComponent } from './features/profile/own-profile-page.component';
import { CompleteProfilePageComponent } from './features/complete-profile/complete-profile-page.component';
import { ProfileEditRequestsInboxPageComponent } from './features/profile-edit-requests/profile-edit-requests-inbox-page.component';
import { ChatPageComponent } from './features/chat/chat-page.component';
import { ConversationDetailComponent } from './features/chat/conversation-detail.component';
import { NewConversationDialogComponent } from './features/chat/new-conversation-dialog.component';
import { SupportPageComponent } from './features/support/support-page.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'select-tenant', component: SelectTenantPageComponent },
  // No guard: universal to any authenticated session regardless of tenant context
  // (SPEC judgment call 2/3) — an unauthenticated visit degrades to the existing
  // generic network-error UI on the first API call, exactly like elsewhere in this app.
  { path: 'profile', component: OwnProfilePageComponent },
  // No guard (PLAN.md's AppSec review, bootstrap-profile-completion): enforcement lives
  // server-side — an unauthenticated caller 401s on the one call this screen makes, and an
  // already-complete account 409 PROFILE_ALREADY_COMPLETE's, which the component treats as
  // success (REQ-9) rather than exposing any other account's data.
  { path: 'complete-profile', component: CompleteProfilePageComponent },
  // No guard: GET /api/profile-edit-requests never 403s, it returns an empty list for a
  // caller with no applicable right — same reasoning already established for staffGuard
  // not being needed on /api/staff/permissions callers.
  { path: 'profile-edit-requests', component: ProfileEditRequestsInboxPageComponent },
  {
    path: 'welcome',
    component: WelcomePageComponent,
    canActivate: [tenantSelectionGuard],
  },
  {
    path: 'tenants/new',
    component: TenantCreatePageComponent,
    canActivate: [staffGuard],
  },
  {
    path: 'dashboard',
    component: DashboardWrapperPageComponent,
    canActivate: [tenantSelectionGuard],
  },
  {
    path: 'members',
    component: UserManagementPageComponent,
    canActivate: [tenantSelectionGuard],
  },
  // REQ-20 (staff-members-management-redesign): access groups are their own
  // screen, independent of any user's detail view. Guarded the same fixed
  // way as staffGuard (GET /api/staff/permissions, which never 403s), gated
  // on STAFF_PERMISSION_MANAGE — the permission every access-group endpoint
  // this screen calls already requires server-side.
  {
    path: 'staff/access-groups',
    component: AccessGroupManagementPageComponent,
    canActivate: [accessGroupManagementGuard],
  },
  {
    path: 'conversations',
    component: ConversationsPageComponent,
    canActivate: [tenantSelectionGuard],
  },
  {
    path: 'articles',
    component: ArticlesPageComponent,
    canActivate: [tenantSelectionGuard],
  },
  // No guard: REQ-1 makes peer messaging available to "any staff or tenant member... regardless
  // of role", and STAFF_ADMIN oversight (REQ-7) spans every tenant, which only works for a
  // staff session with no single active tenant selected.
  {
    path: 'chat',
    component: ChatPageComponent,
    children: [
      { path: 'new', component: NewConversationDialogComponent },
      { path: ':conversationId', component: ConversationDetailComponent },
    ],
  },
  // No guard: REQ-10..18 make the Support screen's own three-way dispatch (staff inbox,
  // member-browse, own channel) a permission check inside SupportPageComponent itself
  // (mirroring staffGuard's fixed pattern), not a route guard — see PLAN.md's rationale.
  // Deviation from PLAN.md's routing table: `:channelId` is read directly by
  // SupportPageComponent (via `ActivatedRoute.paramMap`) rather than through a nested
  // `<router-outlet>` to a separate child component, since the same component already
  // owns the three-way dispatch and there is no distinct child view to route to — a plain
  // second route to the same component is enough.
  { path: 'support', component: SupportPageComponent },
  { path: 'support/:channelId', component: SupportPageComponent },
  {
    path: '',
    pathMatch: 'full',
    component: RootRedirectPlaceholderComponent,
    canActivate: [rootRedirectGuard],
  },
];
