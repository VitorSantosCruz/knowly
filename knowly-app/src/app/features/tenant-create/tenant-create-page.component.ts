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
    <div
      data-testid="tenant-create-page"
      class="mx-auto flex min-h-dvh max-w-md flex-col justify-center p-6"
    >
      <div
        class="enter-fluid w-full rounded-2xl border border-ink-200/70 bg-white p-8 shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      >
        <h1
          class="font-display mb-6 text-2xl font-semibold tracking-tight text-ink-900 dark:text-white"
        >
          {{ 'tenantCreate.title' | transloco }}
        </h1>

        <form
          data-testid="tenant-create-form"
          class="flex flex-col gap-4"
          (submit)="onSubmit($event)"
        >
          <label class="flex flex-col gap-1.5">
            <span class="text-sm font-medium text-ink-700 dark:text-ink-300">{{
              'tenantCreate.name' | transloco
            }}</span>
            <input
              data-testid="tenant-create-name"
              type="text"
              [value]="name()"
              (input)="name.set($any($event.target).value)"
              class="rounded-xl border border-ink-300/70 bg-white px-4 py-2.5 text-sm text-ink-900 shadow-sm transition-shadow duration-fast ease-fluid focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
            />
          </label>

          <label class="flex flex-col gap-1.5">
            <span class="text-sm font-medium text-ink-700 dark:text-ink-300">{{
              'tenantCreate.adminEmail' | transloco
            }}</span>
            <input
              data-testid="tenant-create-admin-email"
              type="email"
              [value]="adminEmail()"
              (input)="adminEmail.set($any($event.target).value)"
              class="rounded-xl border border-ink-300/70 bg-white px-4 py-2.5 text-sm text-ink-900 shadow-sm transition-shadow duration-fast ease-fluid focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
            />
          </label>

          @if (errorMessage(); as message) {
            <p
              data-testid="tenant-create-error"
              class="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400"
            >
              {{ message | transloco }}
            </p>
          }

          <button
            type="submit"
            [disabled]="submitting()"
            class="rounded-xl bg-ink-800 px-4 py-2.5 text-sm font-semibold text-white shadow-sm shadow-ink-900/20 transition-colors duration-fast ease-fluid hover:bg-signal-600 active:bg-signal-700 disabled:cursor-not-allowed disabled:bg-ink-200 disabled:text-ink-400 disabled:shadow-none dark:bg-ink-600 dark:hover:bg-signal-500 dark:disabled:bg-ink-800"
          >
            {{ 'tenantCreate.submit' | transloco }}
          </button>
        </form>
      </div>
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
