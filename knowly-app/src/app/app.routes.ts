import { Routes } from '@angular/router';
import { LoginPageComponent } from './features/login/login-page.component';
import { WelcomePageComponent } from './features/welcome/welcome-page.component';
import { DashboardPageComponent } from './features/dashboard/dashboard-page.component';
import { UserManagementPageComponent } from './features/user-management/user-management-page.component';
import { ConversationsPageComponent } from './features/conversations/conversations-page.component';
import { ArticlesPageComponent } from './features/articles/articles-page.component';
import { SelectTenantPageComponent } from './features/select-tenant/select-tenant-page.component';
import { TenantCreatePageComponent } from './features/tenant-create/tenant-create-page.component';
import { tenantSelectionGuard } from './core/tenant-selection.guard';
import { staffGuard } from './core/staff.guard';
import { rootRedirectGuard } from './core/root-redirect.guard';
import { RootRedirectPlaceholderComponent } from './core/root-redirect-placeholder.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'select-tenant', component: SelectTenantPageComponent },
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
    component: DashboardPageComponent,
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
