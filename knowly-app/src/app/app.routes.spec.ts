import { routes } from './app.routes';
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
});
