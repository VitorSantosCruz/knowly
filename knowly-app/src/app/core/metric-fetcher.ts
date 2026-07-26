import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';

export type MetricError = 'network' | 'permission-denied';

export interface MetricFetcher<T> {
  readonly data: () => T | null;
  readonly loading: () => boolean;
  readonly error: () => MetricError | null;
  readonly traceId: () => string | undefined;
  load(params?: Record<string, string>): void;
}

function extractTraceId(response: HttpErrorResponse): string | undefined {
  const header = response.headers.get('traceparent');
  if (!header) {
    return undefined;
  }

  const parts = header.split('-');
  return parts.length >= 2 ? parts[1] : header;
}

export function createMetricFetcher<T>(http: HttpClient, url: string): MetricFetcher<T> {
  const data = signal<T | null>(null);
  const loading = signal(false);
  const error = signal<MetricError | null>(null);
  const traceId = signal<string | undefined>(undefined);

  return {
    data,
    loading,
    error,
    traceId,
    load(params?: Record<string, string>): void {
      loading.set(true);
      error.set(null);

      http.get<T>(url, { params }).subscribe({
        next: (value) => {
          data.set(value);
          loading.set(false);
        },
        error: (response: HttpErrorResponse) => {
          loading.set(false);

          if (response.status === 403 && response.error?.code === 'PERMISSION_DENIED') {
            error.set('permission-denied');
          } else {
            error.set('network');
            traceId.set(extractTraceId(response));
          }
        },
      });
    },
  };
}
