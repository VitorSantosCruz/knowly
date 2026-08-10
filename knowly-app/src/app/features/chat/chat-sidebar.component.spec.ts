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
  });

  it('renders both direct actions, each keyboard-navigable with an aria-label, when a tenant is active', () => {
    fixture.componentRef.setInput('hasActiveTenant', true);
    fixture.detectChanges();
    for (const testId of ['chat-sidebar-action-articles', 'chat-sidebar-action-create-group']) {
      const button = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
      expect(button).toBeTruthy();
      expect(button.tagName).toBe('BUTTON');
      expect(button.getAttribute('aria-label')).toBeTruthy();
    }
  });

  it('hides "Falar com a base de artigos" without an active tenant, but keeps "Criar grupo" (bug fix: staff-without-tenant oversight view)', () => {
    fixture.componentRef.setInput('hasActiveTenant', false);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-articles"]'),
    ).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-create-group"]'),
    ).toBeTruthy();
  });

  it('emits openArticles when the articles action is clicked', () => {
    fixture.componentRef.setInput('hasActiveTenant', true);
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.openArticles.subscribe(() => (emitted = true));
    fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-articles"]').click();
    expect(emitted).toBe(true);
  });

  it('emits createGroup when the create-group action is clicked', () => {
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.createGroup.subscribe(() => (emitted = true));
    fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-create-group"]').click();
    expect(emitted).toBe(true);
  });

  it('Amended (2026-08-10): no longer renders a search-messages action button — the unified persistent search bar is the only entry point now', () => {
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-sidebar-action-search"]'),
    ).toBeNull();
  });
});
