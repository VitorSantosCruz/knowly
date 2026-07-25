import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

interface OnboardingStatus {
  completed: boolean;
}

@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly http = inject(HttpClient);

  private readonly _completed = signal<boolean | null>(null);
  readonly completed = this._completed.asReadonly();

  fetch(): void {
    this.http
      .get<OnboardingStatus>('/api/users/me/onboarding-status')
      .subscribe((status) => this._completed.set(status.completed));
  }

  markComplete(): Observable<void> {
    return this.http
      .post<void>('/api/users/me/onboarding-complete', {})
      .pipe(tap(() => this._completed.set(true)));
  }
}
