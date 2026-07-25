import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ActiveTenantService } from '../../core/active-tenant.service';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-tenant-create-page',
  imports: [TranslocoPipe],
  template: `
    <div data-testid="tenant-create-page" class="mx-auto max-w-md p-6">
      <h1 class="mb-4 text-lg font-semibold text-slate-900 dark:text-white">
        {{ 'tenantCreate.title' | transloco }}
      </h1>

      <form
        data-testid="tenant-create-form"
        class="flex flex-col gap-3"
        (submit)="onSubmit($event)"
      >
        <label class="flex flex-col gap-1">
          <span class="text-sm text-slate-700 dark:text-slate-300">{{
            'tenantCreate.name' | transloco
          }}</span>
          <input
            data-testid="tenant-create-name"
            type="text"
            [value]="name()"
            (input)="name.set($any($event.target).value)"
            class="rounded border border-slate-300 px-2 py-1"
          />
        </label>

        <label class="flex flex-col gap-1">
          <span class="text-sm text-slate-700 dark:text-slate-300">{{
            'tenantCreate.adminEmail' | transloco
          }}</span>
          <input
            data-testid="tenant-create-admin-email"
            type="email"
            [value]="adminEmail()"
            (input)="adminEmail.set($any($event.target).value)"
            class="rounded border border-slate-300 px-2 py-1"
          />
        </label>

        @if (errorMessage(); as message) {
          <p data-testid="tenant-create-error" class="text-sm text-red-600">
            {{ message | transloco }}
          </p>
        }

        <button
          type="submit"
          [disabled]="submitting()"
          class="rounded bg-indigo-600 px-3 py-1 text-white disabled:opacity-50"
        >
          {{ 'tenantCreate.submit' | transloco }}
        </button>
      </form>
    </div>
  `,
})
export class TenantCreatePageComponent {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly router = inject(Router);

  protected readonly name = signal('');
  protected readonly adminEmail = signal('');
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected onSubmit(event: Event): void {
    event.preventDefault();

    const name = this.name().trim();
    const adminEmail = this.adminEmail().trim();

    if (!name || !EMAIL_PATTERN.test(adminEmail)) {
      this.errorMessage.set('tenantCreate.invalid');
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);

    this.activeTenantService
      .createTenant(name, adminEmail)
      .pipe(
        catchError(() => {
          this.errorMessage.set('tenantCreate.submitError');
          return of(null);
        }),
      )
      .subscribe((result) => {
        this.submitting.set(false);

        if (result !== null) {
          this.router.navigateByUrl('/select-tenant');
        }
      });
  }
}
