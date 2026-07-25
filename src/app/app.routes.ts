import { Routes } from '@angular/router';
import { LoginPageComponent } from './features/login/login-page.component';
import { DashboardPageComponent } from './features/dashboard/dashboard-page.component';
import { MembersPageComponent } from './features/members/members-page.component';
import { ConversationsPageComponent } from './features/conversations/conversations-page.component';
import { ArticlesPageComponent } from './features/articles/articles-page.component';
import { SelectTenantPageComponent } from './features/select-tenant/select-tenant-page.component';
import { TenantCreatePageComponent } from './features/tenant-create/tenant-create-page.component';
import { tenantSelectionGuard } from './core/tenant-selection.guard';
import { staffGuard } from './core/staff.guard';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'select-tenant', component: SelectTenantPageComponent },
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
    component: MembersPageComponent,
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
  { path: '', pathMatch: 'full', redirectTo: 'login' },
];
