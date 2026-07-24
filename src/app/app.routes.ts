import { Routes } from '@angular/router';
import { LoginPageComponent } from './features/login/login-page.component';
import { DashboardPageComponent } from './features/dashboard/dashboard-page.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'dashboard', component: DashboardPageComponent },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
];
