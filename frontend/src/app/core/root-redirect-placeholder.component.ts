import { Component } from '@angular/core';

/**
 * Never actually renders — the '' route's rootRedirectGuard always returns a UrlTree
 * (to /dashboard or /login), but Angular's router still requires a component for the route.
 */
@Component({
  selector: 'app-root-redirect-placeholder',
  template: '',
})
export class RootRedirectPlaceholderComponent {}
