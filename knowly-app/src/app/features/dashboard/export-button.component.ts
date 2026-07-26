import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from 'primeng/button';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { Period } from './period-filter.component';

function extractTraceId(response: HttpErrorResponse): string | undefined {
  const header = response.headers.get('traceparent');
  if (!header) {
    return undefined;
  }

  const parts = header.split('-');
  return parts.length >= 2 ? parts[1] : header;
}

function extractFilename(response: { headers: { get(name: string): string | null } }): string {
  const header = response.headers.get('content-disposition');
  const match = header?.match(/filename="?([^";]+)"?/);
  return match ? match[1] : 'dashboard.csv';
}

@Component({
  selector: 'app-export-button',
  imports: [ButtonDirective, TranslocoPipe, ErrorStateComponent],
  template: `
    <div>
      <button
        data-testid="export-button"
        pButton
        type="button"
        [loading]="loading()"
        (click)="onExport()"
      >
        {{ 'dashboard.export' | transloco }}
      </button>
      @if (error()) {
        <app-error-state [traceId]="traceId()" />
      }
    </div>
  `,
})
export class ExportButtonComponent {
  private readonly http = inject(HttpClient);

  readonly period = input.required<Period>();

  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly traceId = signal<string | undefined>(undefined);

  protected onExport(): void {
    this.loading.set(true);
    this.error.set(false);

    this.http
      .get('/api/tenants/metrics/export', {
        params: { period: this.period() },
        responseType: 'blob',
        observe: 'response',
      })
      .subscribe({
        next: (response) => {
          this.loading.set(false);
          const blob = response.body;
          if (!blob) {
            return;
          }

          const filename = extractFilename(response);
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = filename;
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: (response: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(true);
          this.traceId.set(extractTraceId(response));
        },
      });
  }
}
