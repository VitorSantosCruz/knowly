import { Routes } from '@angular/router';
import { LoginPageComponent } from './features/login/login-page.component';
import { DashboardPageComponent } from './features/dashboard/dashboard-page.component';
import { MembersPageComponent } from './features/members/members-page.component';
import { ConversationsPageComponent } from './features/conversations/conversations-page.component';
import { ArticlesPageComponent } from './features/articles/articles-page.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'dashboard', component: DashboardPageComponent },
  { path: 'members', component: MembersPageComponent },
  { path: 'conversations', component: ConversationsPageComponent },
  { path: 'articles', component: ArticlesPageComponent },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
];
