import { Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

export type ChatSection = 'people' | 'groups' | 'support' | 'articles';

const SECTIONS: { value: ChatSection; labelKey: string }[] = [
  { value: 'people', labelKey: 'chat.sidebar.sectionPeople' },
  { value: 'groups', labelKey: 'chat.sidebar.sectionGroups' },
  { value: 'support', labelKey: 'chat.sidebar.sectionSupport' },
  { value: 'articles', labelKey: 'chat.sidebar.sectionArticles' },
];

/**
 * REQ-2's 4 always-visible section tabs (People/Groups/Support/Base de artigos) — purely
 * presentational, the host (`ChatShellComponent`) owns the actual `section` query-param
 * navigation on `sectionChange`. The search field itself lives in
 * `chat-directory.component.ts` (which owns the search state, per PLAN.md's "State and data"),
 * not duplicated here — a deviation from the components table's literal "4 section tabs +
 * search input" description, made to avoid prop-drilling a query string shell → sidebar →
 * directory for state that already lives one level down.
 */
@Component({
  selector: 'app-chat-sidebar',
  imports: [TranslocoPipe],
  template: `
    <nav data-testid="chat-sidebar" aria-label="Conversas" class="flex flex-col gap-1">
      @for (item of sections; track item.value) {
        <button
          type="button"
          [attr.data-testid]="'chat-sidebar-tab-' + item.value"
          [attr.aria-label]="item.labelKey | transloco"
          [attr.aria-current]="activeSection() === item.value ? 'page' : null"
          (click)="sectionChange.emit(item.value)"
          class="rounded-lg px-3 py-2 text-left text-sm font-medium hover:bg-ink-50 dark:hover:bg-ink-800"
          [class.bg-signal-100]="activeSection() === item.value"
          [class.dark:bg-signal-900]="activeSection() === item.value"
        >
          {{ item.labelKey | transloco }}
        </button>
      }
    </nav>
  `,
})
export class ChatSidebarComponent {
  readonly activeSection = input.required<ChatSection>();
  readonly sectionChange = output<ChatSection>();

  protected readonly sections = SECTIONS;
}
