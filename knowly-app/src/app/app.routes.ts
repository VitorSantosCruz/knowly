import { Routes } from '@angular/router';
import { LoginPageComponent } from './features/login/login-page.component';
import { WelcomePageComponent } from './features/welcome/welcome-page.component';
import { DashboardWrapperPageComponent } from './features/dashboard/dashboard-wrapper-page.component';
import { UserManagementPageComponent } from './features/user-management/user-management-page.component';
import { ArticlesPageComponent } from './features/articles/articles-page.component';
import { SelectTenantPageComponent } from './features/select-tenant/select-tenant-page.component';
import { TenantCreatePageComponent } from './features/tenant-create/tenant-create-page.component';
import { tenantSelectionGuard } from './core/tenant-selection.guard';
import { staffGuard } from './core/staff.guard';
import { accessGroupManagementGuard } from './core/access-group-management.guard';
import { AccessGroupManagementPageComponent } from './features/access-groups/access-group-management-page.component';
import { tenantAccessGroupManagementGuard } from './core/tenant-access-group-management.guard';
import { TenantAccessGroupManagementPageComponent } from './features/access-groups/tenant-access-group-management-page.component';
import { rootRedirectGuard } from './core/root-redirect.guard';
import { RootRedirectPlaceholderComponent } from './core/root-redirect-placeholder.component';
import { OwnProfilePageComponent } from './features/profile/own-profile-page.component';
import { CompleteProfilePageComponent } from './features/complete-profile/complete-profile-page.component';
import { ProfileEditRequestsInboxPageComponent } from './features/profile-edit-requests/profile-edit-requests-inbox-page.component';
import { ChatShellComponent } from './features/chat/chat-shell.component';

/** Small helper for the `/support`/`/conversations` redirects below — preserves any other
 * existing query params (e.g. a hypothetical `?debug=1`) alongside the new `section` one,
 * rather than a bare string `redirectTo` that would drop them. */
function buildQueryOnlyRedirect(path: string, queryParams: Record<string, string>): string {
  const search = new URLSearchParams(queryParams).toString();
  return search ? `${path}?${search}` : path;
}

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
  // REQ-2 (tenant-access-group-management): tenantSelectionGuard establishes there *is* an
  // active tenant before tenantAccessGroupManagementGuard's permission check, which needs one,
  // runs -- guard order matters here.
  {
    path: 'tenants/access-groups',
    component: TenantAccessGroupManagementPageComponent,
    canActivate: [tenantSelectionGuard, tenantAccessGroupManagementGuard],
  },
  {
    path: 'articles',
    component: ArticlesPageComponent,
    canActivate: [tenantSelectionGuard],
  },
  // chat-unified-ui (REQ-1/REQ-2): a single "Conversas" nav entry replaces the three previously
  // separate /chat, /support, /conversations top-level routes — no route guard on any of these
  // (same reasoning as before: peer messaging/STAFF_ADMIN oversight/Support's own permission
  // dispatch all need to work with no single active tenant selected). ChatShellComponent itself
  // decides which of the 4 sections (People/Groups/Support/Base de artigos) to render, off
  // `data.chatSection` for the 3 id-carrying routes below and the `section` query param for the
  // bare path — see that component's own doc comment. `support/:channelId` and
  // `articles/:conversationId` are registered as distinct, deeper paths (not reused query
  // params) specifically so each disambiguates which service resolves its id — a bare
  // `/chat/:conversationId` alone would otherwise collide with the support-channel and
  // RAG-conversation id spaces, which are independently-minted BIGSERIALs starting at 1 each.
  { path: 'chat', component: ChatShellComponent },
  {
    path: 'chat/support/:channelId',
    component: ChatShellComponent,
    data: { chatSection: 'support' },
  },
  {
    path: 'chat/articles/:conversationId',
    component: ChatShellComponent,
    data: { chatSection: 'articles' },
  },
  { path: 'chat/:conversationId', component: ChatShellComponent, data: { chatSection: 'peer' } },
  // Old top-level routes become redirects into the new nested shell rather than being removed
  // outright — a low-cost safety net for a bookmarked URL or a saved link in a support ticket,
  // per PLAN.md ("never break a resolvable URL without a documented reason").
  {
    path: 'support',
    pathMatch: 'full',
    redirectTo: (redirectData) =>
      buildQueryOnlyRedirect('/chat', { ...redirectData.queryParams, section: 'support' }),
  },
  { path: 'support/:channelId', redirectTo: '/chat/support/:channelId' },
  {
    path: 'conversations',
    pathMatch: 'full',
    redirectTo: (redirectData) =>
      buildQueryOnlyRedirect('/chat', { ...redirectData.queryParams, section: 'articles' }),
  },
  {
    path: '',
    pathMatch: 'full',
    component: RootRedirectPlaceholderComponent,
    canActivate: [rootRedirectGuard],
  },
];
