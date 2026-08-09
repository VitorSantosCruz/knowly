import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ConversationDetail } from '../../core/chat.model';
import { ChatHeaderComponent } from './chat-header.component';

describe('ChatHeaderComponent', () => {
  let fixture: ComponentFixture<ChatHeaderComponent>;
  let httpMock: HttpTestingController;

  const detail: ConversationDetail = {
    id: 1,
    kind: 'PEER_GROUP',
    tenantId: 5,
    title: null,
    participantUserIds: [1, 2],
    participantNicknames: { 1: 'Alice', 2: 'Bob' },
    visibility: 'PRIVATE',
    archivedAt: null,
    adminUserIds: [1],
    icon: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatHeaderComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ChatHeaderComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('detail', detail);
  });

  afterEach(() => httpMock.verify());

  describe('Amendment (4), REQ-40: group rename affordance', () => {
    it('does not render the pencil for a non-admin viewer', () => {
      fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
      fixture.componentRef.setInput('currentUserId', 2);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="chat-header-rename"]')).toBeNull();
    });

    it('renders the pencil for the group admin', () => {
      fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
      fixture.componentRef.setInput('currentUserId', 1);
      fixture.detectChanges();

      expect(
        fixture.nativeElement.querySelector('[data-testid="chat-header-rename"]'),
      ).toBeTruthy();
    });

    it('does not render the pencil for a 1:1 conversation, admin or not', () => {
      fixture.componentRef.setInput('detail', { ...detail, kind: 'PEER_DIRECT' });
      fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
      fixture.componentRef.setInput('currentUserId', 1);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="chat-header-rename"]')).toBeNull();
    });

    it('activating it opens the inline rename form prefilled with the current title/icon', () => {
      fixture.componentRef.setInput('detail', { ...detail, title: 'Grupo' });
      fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
      fixture.componentRef.setInput('currentUserId', 1);
      fixture.detectChanges();

      fixture.nativeElement.querySelector('[data-testid="chat-header-rename"]').click();
      fixture.detectChanges();

      const nameInput: HTMLInputElement = fixture.nativeElement.querySelector(
        '[data-testid="rename-form-name-input"]',
      );
      expect(nameInput.value).toBe('Grupo');
      expect(
        fixture.nativeElement.querySelector('[data-testid="chat-header-open-info"]'),
      ).toBeNull();
    });

    it('saving calls ChatGroupService.rename and closes the form on success', () => {
      fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
      fixture.componentRef.setInput('currentUserId', 1);
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="chat-header-rename"]').click();
      fixture.detectChanges();

      const nameInput: HTMLInputElement = fixture.nativeElement.querySelector(
        '[data-testid="rename-form-name-input"]',
      );
      nameInput.value = 'Novo nome';
      nameInput.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="rename-form-save"]').click();

      const req = httpMock.expectOne('/api/chat/conversations/1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ title: 'Novo nome', icon: undefined });
      req.flush({ ...detail, title: 'Novo nome' });
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="rename-form"]')).toBeNull();
    });

    it('a failed rename (400/403/404) renders the same generic error text and leaves the form open', () => {
      fixture.componentRef.setInput('detail', { ...detail, title: 'Grupo' });
      fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
      fixture.componentRef.setInput('currentUserId', 1);
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="chat-header-rename"]').click();
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="rename-form-save"]').click();
      httpMock
        .expectOne('/api/chat/conversations/1')
        .flush(null, { status: 403, statusText: 'Forbidden' });
      fixture.detectChanges();
      const message403 = fixture.nativeElement.querySelector(
        '[data-testid="rename-form-error"]',
      ).textContent;

      fixture.nativeElement.querySelector('[data-testid="rename-form-save"]').click();
      httpMock
        .expectOne('/api/chat/conversations/1')
        .flush(null, { status: 404, statusText: 'Not Found' });
      fixture.detectChanges();
      const message404 = fixture.nativeElement.querySelector(
        '[data-testid="rename-form-error"]',
      ).textContent;

      expect(message403).toBe(message404);
      expect(fixture.nativeElement.querySelector('[data-testid="rename-form"]')).toBeTruthy();
    });
  });

  it('renders participant nicknames normally for PARTICIPANT, no banner', () => {
    fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Alice');
    expect(
      fixture.nativeElement.querySelector('[data-testid="chat-header-looking-in-banner"]'),
    ).toBeNull();
  });

  it('renders the avatar/icon alongside the title', () => {
    fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="avatar-fallback"]')).toBeTruthy();
  });

  it('the icon+name is a clickable button that emits openInfo on click', () => {
    fixture.componentRef.setInput('viewerRelation', 'PARTICIPANT');
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="chat-header-open-info"]',
    );
    expect(button).toBeTruthy();
    expect(button.tagName).toBe('BUTTON');
    expect(button.getAttribute('aria-label')).toBeTruthy();

    let emitted = false;
    fixture.componentInstance.openInfo.subscribe(() => (emitted = true));
    button.click();

    expect(emitted).toBe(true);
  });

  it('renders a distinct oversight banner, non-"joined" copy, for LOOKING_IN', () => {
    fixture.componentRef.setInput('viewerRelation', 'LOOKING_IN');
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector(
      '[data-testid="chat-header-looking-in-banner"]',
    );
    expect(banner).toBeTruthy();
    expect(banner.getAttribute('aria-label')).toBeTruthy();
    expect(banner.textContent).not.toMatch(/joined/i);
  });
});
