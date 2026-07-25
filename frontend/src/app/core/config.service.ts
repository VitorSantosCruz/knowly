import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface AppConfig {
  turnstileSiteKey: string;
}

const DEFAULT_CONFIG: AppConfig = {
  turnstileSiteKey: '',
};

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private readonly http = inject(HttpClient);
  private config: AppConfig = DEFAULT_CONFIG;

  get turnstileSiteKey(): string {
    return this.config.turnstileSiteKey;
  }

  async load(): Promise<void> {
    try {
      this.config = await firstValueFrom(this.http.get<AppConfig>('/config.json'));
    } catch {
      // config.json is served as a static file generated per environment (see
      // public/config.example.json); its absence shouldn't block the app from
      // rendering, just leave features that depend on it (e.g. Turnstile) inert.
      this.config = DEFAULT_CONFIG;
    }
  }
}
