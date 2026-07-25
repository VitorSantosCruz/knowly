import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { installBfcacheReload } from './app/core/bfcache-reload';

installBfcacheReload();

bootstrapApplication(App, appConfig).catch((err) => console.error(err));
