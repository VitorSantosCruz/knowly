import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { routes } from './app.routes';
import { ChatShellComponent } from './features/chat/chat-shell.component';
import { OwnProfilePageComponent } from './features/profile/own-profile-page.component';
import { ProfileEditRequestsInboxPageComponent } from './features/profile-edit-requests/profile-edit-requests-inbox-page.component';
import { TenantAccessGroupManagementPageComponent } from './features/access-groups/tenant-access-group-management-page.component';
import { tenantSelectionGuard } from './core/tenant-selection.guard';
import { tenantAccessGroupManagementGuard } from './core/tenant-access-group-management.guard';

describe('app.routes', () => {
  it('resolves /profile to OwnProfilePageComponent with no guard', () => {
    const route = routes.find((r) => r.path === 'profile');

    expect(route?.component).toBe(OwnProfilePageComponent);
    expect(route?.canActivate).toBeUndefined();
  });

  it('resolves /profile-edit-requests to ProfileEditRequestsInboxPageComponent with no guard', () => {
    const route = routes.find((r) => r.path === 'profile-edit-requests');

    expect(route?.component).toBe(ProfileEditRequestsInboxPageComponent);
    expect(route?.canActivate).toBeUndefined();
  });

  it('resolves /tenants/access-groups to TenantAccessGroupManagementPageComponent behind tenantSelectionGuard then tenantAccessGroupManagementGuard, in that order', () => {
    const route = routes.find((r) => r.path === 'tenants/access-groups');

    expect(route?.component).toBe(TenantAccessGroupManagementPageComponent);
    expect(route?.canActivate).toEqual([tenantSelectionGuard, tenantAccessGroupManagementGuard]);
  });

  it('resolves /chat, /chat/:conversationId, /chat/support/:channelId, and /chat/articles/:conversationId to ChatShellComponent with no guard', () => {
    for (const path of [
      'chat',
      'chat/:conversationId',
      'chat/support/:channelId',
      'chat/articles/:conversationId',
    ]) {
      const route = routes.find((r) => r.path === path);
      expect(route?.component).toBe(ChatShellComponent);
      expect(route?.canActivate).toBeUndefined();
    }
  });

  describe('old /support and /conversations routes redirect into the new /chat shell', () => {
    let router: Router;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
      });
      router = TestBed.inject(Router);
    });

    it('/support redirects to /chat?section=support', async () => {
      await router.navigateByUrl('/support');
      expect(router.url).toBe('/chat?section=support');
    });

    it('/support/:channelId redirects to /chat/support/:channelId', async () => {
      await router.navigateByUrl('/support/42');
      expect(router.url).toBe('/chat/support/42');
    });

    it('/conversations redirects to /chat?section=articles', async () => {
      await router.navigateByUrl('/conversations');
      expect(router.url).toBe('/chat?section=articles');
    });
  });
});
