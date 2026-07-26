import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';
import { TranslocoHttpLoader } from './i18n/transloco-loader';
import { ConfigService } from './core/config.service';
import { authInterceptor } from './core/auth.interceptor';
import { InkSignalPreset } from './core/prime-theme';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([authInterceptor]),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    // `darkModeSelector: '.dark'` reuses the app's existing light/dark toggle
    // (ThemeService toggles `.dark` on <html>) instead of introducing
    // PrimeNG's own default `.p-dark` convention as a second mechanism.
    providePrimeNG({
      theme: {
        preset: InkSignalPreset,
        options: {
          darkModeSelector: '.dark',
        },
      },
    }),
    provideTransloco({
      config: {
        availableLangs: ['en', 'pt-BR'],
        defaultLang: 'en',
        reRenderOnLangChange: true,
      },
      loader: TranslocoHttpLoader,
    }),
    provideAppInitializer(() => inject(ConfigService).load()),
  ],
};
