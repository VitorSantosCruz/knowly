import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ProfileEditRequest, ProfileService } from '../../core/profile.service';
import { ErrorStateComponent } from '../../shared/error-state.component';

@Component({
  selector: 'app-profile-edit-requests-inbox-page',
  imports: [TranslocoPipe, ErrorStateComponent],
  template: `
    <div data-testid="profile-edit-requests-inbox-page" class="page-shell">
      <h1 class="mb-4 font-semibold text-ink-900 dark:text-white">
        {{ 'profileEditRequests.title' | transloco }}
      </h1>

      @if (error()) {
        <app-error-state />
      }

      @if (requests().length === 0 && !error()) {
        <p data-testid="profile-edit-requests-empty" class="text-sm text-ink-500 dark:text-ink-400">
          {{ 'profileEditRequests.empty' | transloco }}
        </p>
      } @else {
        <ul class="flex flex-col gap-3">
          @for (request of requests(); track request.id) {
            <li
              [attr.data-testid]="'profile-edit-request-' + request.id"
              class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-4 shadow-sm shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
            >
              <p class="text-sm font-medium text-ink-900 dark:text-white">
                @if (requesterDisplayName(request); as name) {
                  {{ 'profileEditRequests.requesterNamed' | transloco: { name } }}
                } @else {
                  {{
                    'profileEditRequests.requester' | transloco: { userId: request.requesterUserId }
                  }}
                }
              </p>
              <p class="text-sm text-ink-600 dark:text-ink-400">
                {{ request.proposedFields.fullName }} · {{ request.proposedFields.rg }} ·
                {{ request.proposedFields.cpf }} · {{ request.proposedFields.rgOrgaoEmissor }} ·
                {{ request.proposedFields.birthDate }}
              </p>
              @if (request.proposedFields.address; as address) {
                <p
                  [attr.data-testid]="'profile-edit-request-address-' + request.id"
                  class="text-sm text-ink-600 dark:text-ink-400"
                >
                  {{ address.logradouro }}, {{ address.numero }} - {{ address.bairro }},
                  {{ address.cidade }}/{{ address.estado }} - {{ address.cep }} -
                  {{ address.pais }}
                </p>
              }
              @if (request.proposedContactChanges.length > 0) {
                <ul
                  [attr.data-testid]="'profile-edit-request-contact-changes-' + request.id"
                  class="text-sm text-ink-600 dark:text-ink-400"
                >
                  @for (change of request.proposedContactChanges; track $index) {
                    <li>
                      <span class="font-medium">{{ change.action }}</span>
                      @if (change.type) {
                        · {{ change.type }}
                      }
                      @if (change.value) {
                        · {{ change.value }}
                      }
                      @if (change.label) {
                        · {{ change.label }}
                      }
                    </li>
                  }
                </ul>
              }
              <p class="text-xs text-ink-500 dark:text-ink-400">
                {{ 'profileEditRequests.submittedAt' | transloco }}: {{ request.createdAt }}
              </p>

              @if (conflictMessages()[request.id]; as fields) {
                <p
                  [attr.data-testid]="'conflict-request-' + request.id"
                  class="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/30 dark:text-red-400"
                >
                  {{ 'profileEditRequests.conflict' | transloco: { fields: fields.join(', ') } }}
                </p>
              }

              <div class="mt-2 flex gap-2">
                <button
                  [attr.data-testid]="'approve-request-' + request.id"
                  (click)="onApprove(request)"
                  class="rounded-xl bg-ink-800 px-3 py-1.5 text-sm font-medium text-white transition-colors duration-fast ease-fluid hover:bg-signal-600 active:bg-signal-700 dark:bg-ink-600 dark:hover:bg-signal-500"
                >
                  {{ 'profileEditRequests.approve' | transloco }}
                </button>
                <button
                  [attr.data-testid]="'reject-request-' + request.id"
                  (click)="onReject(request)"
                  class="rounded-xl border border-ink-300/70 px-3 py-1.5 text-sm font-medium text-ink-700 transition-colors duration-fast ease-fluid hover:bg-ink-50 dark:border-ink-700 dark:text-ink-300 dark:hover:bg-ink-800/50"
                >
                  {{ 'profileEditRequests.reject' | transloco }}
                </button>
              </div>
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class ProfileEditRequestsInboxPageComponent implements OnInit {
  private readonly profileService = inject(ProfileService);

  protected readonly requests = signal<ProfileEditRequest[]>([]);
  protected readonly error = signal(false);
  protected readonly conflictMessages = signal<Record<number, string[]>>({});

  ngOnInit(): void {
    this.load();
  }

  // Backend fallback chain (PLAN.md follow-up 2026-07-30): requesterName is null for a
  // requester who never filled in a full name yet; falls back to email, then finally to
  // the existing "User #{id}" string when both are null.
  protected requesterDisplayName(request: ProfileEditRequest): string | null {
    return request.requesterName ?? request.requesterEmail ?? null;
  }

  private load(): void {
    this.profileService
      .listEditRequests()
      .pipe(
        catchError(() => {
          this.error.set(true);
          return of(null);
        }),
      )
      .subscribe((requests) => {
        if (requests !== null) {
          this.error.set(false);
          this.requests.set(requests);
        }
      });
  }

  protected onApprove(request: ProfileEditRequest): void {
    this.clearConflict(request.id);

    this.profileService
      .approveEditRequest(request.id)
      .pipe(
        catchError((err) => {
          if (err.status === 409 && err.error?.conflictingFields) {
            this.setConflict(request.id, err.error.conflictingFields);
          } else {
            this.error.set(true);
            this.refreshAfterStaleAction();
          }
          return of('failed' as const);
        }),
      )
      .subscribe((result) => {
        if (result !== 'failed') {
          this.removeRequest(request.id);
        }
      });
  }

  protected onReject(request: ProfileEditRequest): void {
    this.clearConflict(request.id);

    this.profileService
      .rejectEditRequest(request.id)
      .pipe(
        catchError(() => {
          this.error.set(true);
          this.refreshAfterStaleAction();
          return of('failed' as const);
        }),
      )
      .subscribe((result) => {
        if (result !== 'failed') {
          this.removeRequest(request.id);
        }
      });
  }

  // REQ-16: the list still needs to drop the now-stale row even though the caller keeps
  // seeing the error/permission-denied state — so this refresh never clears `error()`,
  // unlike the initial ngOnInit load().
  private refreshAfterStaleAction(): void {
    this.profileService
      .listEditRequests()
      .pipe(catchError(() => of(null)))
      .subscribe((requests) => {
        if (requests !== null) {
          this.requests.set(requests);
        }
      });
  }

  private removeRequest(id: number): void {
    this.requests.update((current) => current.filter((request) => request.id !== id));
  }

  private setConflict(id: number, fields: string[]): void {
    this.conflictMessages.update((current) => ({ ...current, [id]: fields }));
  }

  private clearConflict(id: number): void {
    this.conflictMessages.update((current) => {
      const { [id]: _removed, ...rest } = current;
      return rest;
    });
  }
}
