import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatSidebarComponent } from './chat-sidebar.component';

describe('ChatSidebarComponent', () => {
  let fixture: ComponentFixture<ChatSidebarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatSidebarComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ChatSidebarComponent);
    fixture.componentRef.setInput('activeSection', 'people');
  });

  it('renders the 4 section tabs, each keyboard-navigable with an aria-label', () => {
    fixture.detectChanges();
    for (const section of ['people', 'groups', 'support', 'articles']) {
      const tab = fixture.nativeElement.querySelector(
        `[data-testid="chat-sidebar-tab-${section}"]`,
      );
      expect(tab).toBeTruthy();
      expect(tab.tagName).toBe('BUTTON');
      expect(tab.getAttribute('aria-label')).toBeTruthy();
    }
  });

  it('marks the active section with aria-current', () => {
    fixture.componentRef.setInput('activeSection', 'groups');
    fixture.detectChanges();
    expect(
      fixture.nativeElement
        .querySelector('[data-testid="chat-sidebar-tab-groups"]')
        .getAttribute('aria-current'),
    ).toBe('page');
    expect(
      fixture.nativeElement
        .querySelector('[data-testid="chat-sidebar-tab-people"]')
        .getAttribute('aria-current'),
    ).toBeNull();
  });

  it('emits sectionChange when a tab is clicked, without performing navigation itself', () => {
    fixture.detectChanges();
    let emitted: string | undefined;
    fixture.componentInstance.sectionChange.subscribe((value) => (emitted = value));

    fixture.nativeElement.querySelector('[data-testid="chat-sidebar-tab-support"]').click();

    expect(emitted).toBe('support');
  });
});
