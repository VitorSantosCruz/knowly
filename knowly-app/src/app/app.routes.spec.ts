import { routes } from './app.routes';
import { OwnProfilePageComponent } from './features/profile/own-profile-page.component';

describe('app.routes', () => {
  it('resolves /profile to OwnProfilePageComponent with no guard', () => {
    const route = routes.find((r) => r.path === 'profile');

    expect(route?.component).toBe(OwnProfilePageComponent);
    expect(route?.canActivate).toBeUndefined();
  });
});
