import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-no-active-tenant-state',
  imports: [TranslocoPipe, RouterLink],
  template: `
    <p
      data-testid="no-active-tenant-state"
      class="enter-fluid text-sm text-ink-500 dark:text-ink-400"
    >
      {{ 'common.noActiveTenant' | transloco }}
      <a routerLink="/select-tenant" class="text-signal-600 hover:underline dark:text-signal-400">
        {{ 'nav.switchTenant' | transloco }}
      </a>
    </p>
  `,
})
export class NoActiveTenantStateComponent {}
