import { routes } from './app.routes';
import { OwnProfilePageComponent } from './features/profile/own-profile-page.component';
import { ProfileEditRequestsInboxPageComponent } from './features/profile-edit-requests/profile-edit-requests-inbox-page.component';

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
});
