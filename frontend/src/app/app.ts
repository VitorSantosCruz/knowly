import { Component } from '@angular/core';
import { AppShellComponent } from './layout/app-shell.component';

@Component({
  selector: 'app-root',
  imports: [AppShellComponent],
  template: `<app-shell />`,
})
export class App {}
