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
import { rootRedirectGuard } from './core/root-redirect.guard';
import { RootRedirectPlaceholderComponent } from './core/root-redirect-placeholder.component';
import { OwnProfilePageComponent } from './features/profile/own-profile-page.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'select-tenant', component: SelectTenantPageComponent },
  // No guard: universal to any authenticated session regardless of tenant context
  // (SPEC judgment call 2/3) — an unauthenticated visit degrades to the existing
  // generic network-error UI on the first API call, exactly like elsewhere in this app.
  { path: 'profile', component: OwnProfilePageComponent },
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
  {
    path: '',
    pathMatch: 'full',
    component: RootRedirectPlaceholderComponent,
    canActivate: [rootRedirectGuard],
  },
];
