import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { ArticlesPageComponent } from './articles-page.component';
import { Permission } from '../../core/permission';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

const ALL_ARTICLE_PERMISSIONS: Permission[] = [
  'ARTICLE_VIEW',
  'ARTICLE_CREATE',
  'ARTICLE_EDIT',
  'ARTICLE_DELETE',
];

describe('ArticlesPageComponent', () => {
  let fixture: ComponentFixture<ArticlesPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArticlesPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ArticlesPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushSetup(
    articles: { id: number; title: string; status: string }[],
    permissions: Permission[] = ALL_ARTICLE_PERMISSIONS,
  ) {
    httpMock
      .expectOne('/api/tenants/active')
      .flush({ tenantId: 7, tenantName: 'Acme', role: 'MEMBER_ADMIN' });
    httpMock.expectOne('/api/tenants/permissions').flush({ permissions });
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/7/articles').flush(articles);
    fixture.detectChanges();
  }

  it('renders the article list with title and status on load', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Handbook', status: 'READY' }]);

    expect(fixture.nativeElement.textContent).toContain('Handbook');
    expect(
      fixture.nativeElement.querySelector('[data-testid="article-status-1"]').textContent,
    ).toContain('Ready');
  });

  it('shows a permission-denied state when the list is forbidden', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/active')
      .flush({ tenantId: 7, tenantName: 'Acme', role: 'MEMBER_ADMIN' });
    httpMock.expectOne('/api/tenants/permissions').flush({ permissions: [] });
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/7/articles')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it('selecting an article shows its text and original file link', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Handbook', status: 'READY' }]);

    const selectButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="select-article-1"]',
    );
    selectButton.click();

    httpMock.expectOne('/api/tenants/7/articles/1').flush({
      id: 1,
      title: 'Handbook',
      text: 'Extracted text',
      status: 'READY',
      failureReason: null,
      originalFileUrl: 'https://example.com/handbook.pdf',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Extracted text');
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector(
      '[data-testid="original-file-link"]',
    );
    expect(link.href).toBe('https://example.com/handbook.pdf');
  });

  it('shows the failure reason for a failed article', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Bad file', status: 'FAILED' }]);

    fixture.nativeElement.querySelector('[data-testid="select-article-1"]').click();

    httpMock.expectOne('/api/tenants/7/articles/1').flush({
      id: 1,
      title: 'Bad file',
      text: null,
      status: 'FAILED',
      failureReason: 'Corrupt file',
      originalFileUrl: 'https://example.com/bad.pdf',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Corrupt file');
  });

  it('uploading a supported file adds it to the list as processing', () => {
    fixture.detectChanges();
    flushSetup([]);

    const titleInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="upload-title"]',
    );
    titleInput.value = 'New doc';
    titleInput.dispatchEvent(new Event('input'));

    const file = new File(['content'], 'new.pdf', { type: 'application/pdf' });
    const fileInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="upload-file"]',
    );
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true });
    fileInput.dispatchEvent(new Event('change'));

    fixture.nativeElement
      .querySelector('[data-testid="upload-form"]')
      .dispatchEvent(new Event('submit'));

    const req = httpMock.expectOne('/api/tenants/7/articles');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 9, title: 'New doc', status: 'PROCESSING' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('New doc');
    expect(
      fixture.nativeElement.querySelector('[data-testid="article-status-9"]').textContent,
    ).toContain('Processing');
  });

  it('disables the Upload button until both title and file are provided', () => {
    fixture.detectChanges();
    flushSetup([]);

    const submitButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="upload-submit"]',
    );
    expect(submitButton.disabled).toBe(true);

    const titleInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="upload-title"]',
    );
    titleInput.value = 'New doc';
    titleInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(submitButton.disabled).toBe(true);

    titleInput.value = '';
    titleInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const file = new File(['content'], 'new.pdf', { type: 'application/pdf' });
    const fileInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="upload-file"]',
    );
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true });
    fileInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(submitButton.disabled).toBe(true);

    titleInput.value = 'New doc';
    titleInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(submitButton.disabled).toBe(false);
  });

  it('shows an error and adds nothing when the upload is rejected', () => {
    fixture.detectChanges();
    flushSetup([]);

    const titleInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="upload-title"]',
    );
    titleInput.value = 'Bad upload';
    titleInput.dispatchEvent(new Event('input'));
    const file = new File(['content'], 'malware.exe', { type: 'application/x-msdownload' });
    const fileInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="upload-file"]',
    );
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true });
    fileInput.dispatchEvent(new Event('change'));
    fixture.nativeElement
      .querySelector('[data-testid="upload-form"]')
      .dispatchEvent(new Event('submit'));

    httpMock
      .expectOne('/api/tenants/7/articles')
      .flush({ code: 'UNSUPPORTED_FILE_TYPE' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="upload-error"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toContain('Bad upload');
  });

  it('polls the list while an article is processing, and stops once none remain', () => {
    vi.useFakeTimers();
    try {
      fixture.detectChanges();
      flushSetup([{ id: 1, title: 'Handbook', status: 'PROCESSING' }]);

      vi.advanceTimersByTime(4000);
      httpMock
        .expectOne('/api/tenants/7/articles')
        .flush([{ id: 1, title: 'Handbook', status: 'PROCESSING' }]);
      fixture.detectChanges();

      vi.advanceTimersByTime(4000);
      httpMock
        .expectOne('/api/tenants/7/articles')
        .flush([{ id: 1, title: 'Handbook', status: 'READY' }]);
      fixture.detectChanges();

      vi.advanceTimersByTime(4000);
      httpMock.expectNone('/api/tenants/7/articles');

      fixture.destroy();
    } finally {
      vi.useRealTimers();
    }
  });

  it('does not flicker the full list on a no-op poll tick', () => {
    vi.useFakeTimers();
    try {
      fixture.detectChanges();
      flushSetup([{ id: 1, title: 'Handbook', status: 'PROCESSING' }]);

      const listBefore: HTMLElement = fixture.nativeElement.querySelector(
        '[data-testid="article-list"]',
      );

      vi.advanceTimersByTime(4000);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeNull();

      httpMock
        .expectOne('/api/tenants/7/articles')
        .flush([{ id: 1, title: 'Handbook', status: 'PROCESSING' }]);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeNull();
      const listAfter: HTMLElement = fixture.nativeElement.querySelector(
        '[data-testid="article-list"]',
      );
      expect(listAfter).toBe(listBefore);

      vi.advanceTimersByTime(4000);
      httpMock
        .expectOne('/api/tenants/7/articles')
        .flush([{ id: 1, title: 'Handbook', status: 'READY' }]);
      fixture.detectChanges();

      expect(
        fixture.nativeElement.querySelector('[data-testid="article-status-1"]').textContent,
      ).toContain('Ready');

      fixture.destroy();
    } finally {
      vi.useRealTimers();
    }
  });

  it('editing an article persists the new title/text', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Handbook', status: 'READY' }]);

    fixture.nativeElement.querySelector('[data-testid="select-article-1"]').click();
    httpMock.expectOne('/api/tenants/7/articles/1').flush({
      id: 1,
      title: 'Handbook',
      text: 'Old text',
      status: 'READY',
      failureReason: null,
      originalFileUrl: 'https://example.com/handbook.pdf',
    });
    fixture.detectChanges();

    const textArea: HTMLTextAreaElement = fixture.nativeElement.querySelector(
      '[data-testid="edit-text"]',
    );
    textArea.value = 'Corrected text';
    textArea.dispatchEvent(new Event('input'));
    fixture.nativeElement
      .querySelector('[data-testid="edit-form"]')
      .dispatchEvent(new Event('submit'));

    httpMock.expectOne('/api/tenants/7/articles/1').flush({
      id: 1,
      title: 'Handbook',
      text: 'Corrected text',
      status: 'READY',
      failureReason: null,
      originalFileUrl: 'https://example.com/handbook.pdf',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Corrected text');
  });

  it('deleting an article requires confirmation, naming the article, and requires the fetched word to be retyped', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Handbook', status: 'READY' }]);

    fixture.nativeElement.querySelector('[data-testid="delete-article-1"]').click();
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants/7/articles/1');
    const dialogEl = fixture.nativeElement.querySelector('app-confirm-dialog');
    expect(dialogEl).toBeTruthy();
    expect(dialogEl.textContent).toContain('Handbook');

    httpMock
      .expectOne('/api/tenants/7/articles/1/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    const confirmButton: HTMLButtonElement = dialogEl.querySelector(
      '[data-testid="confirm-dialog-confirm"]',
    );
    expect(confirmButton.disabled).toBe(true);

    const input: HTMLInputElement = dialogEl.querySelector('[data-testid="confirm-dialog-input"]');
    input.value = 'correct-horse';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    confirmButton.click();
    fixture.detectChanges();

    const deleteReq = httpMock.expectOne('/api/tenants/7/articles/1');
    expect(deleteReq.request.body).toEqual({ word: 'correct-horse' });
    deleteReq.flush({});
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Handbook');
  });

  it('re-fetches a fresh word and keeps the article in place when the delete call rejects the word (REQ-8)', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Handbook', status: 'READY' }]);

    fixture.nativeElement.querySelector('[data-testid="delete-article-1"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/articles/1/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    const dialogEl = fixture.nativeElement.querySelector('app-confirm-dialog');
    const input: HTMLInputElement = dialogEl.querySelector('[data-testid="confirm-dialog-input"]');
    input.value = 'correct-horse';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    dialogEl.querySelector('[data-testid="confirm-dialog-confirm"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/articles/1')
      .flush({ code: 'INVALID_TOKEN' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/articles/1/deletion-confirmation-token')
      .flush({ word: 'fresh-word' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Handbook');
    expect(dialogEl.querySelector('[data-testid="confirm-dialog-word"]').textContent).toContain(
      'fresh-word',
    );
  });

  it('cancelling the deletion confirmation leaves the article unchanged', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Handbook', status: 'READY' }]);

    fixture.nativeElement.querySelector('[data-testid="delete-article-1"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/articles/1/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('app-confirm-dialog [data-testid="confirm-dialog-cancel"]')
      .click();
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants/7/articles/1');
    expect(fixture.nativeElement.textContent).toContain('Handbook');
    expect(fixture.nativeElement.querySelector('app-confirm-dialog')).toBeNull();
  });

  it('dismissing the deletion confirmation via the native cancel event leaves the article unchanged', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Handbook', status: 'READY' }]);

    fixture.nativeElement.querySelector('[data-testid="delete-article-1"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/articles/1/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('app-confirm-dialog dialog')
      .dispatchEvent(new Event('cancel'));
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants/7/articles/1');
    expect(fixture.nativeElement.textContent).toContain('Handbook');
  });

  it('renders full width with no article content panel until an article is selected', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Handbook', status: 'READY' }]);

    const aside: HTMLElement = fixture.nativeElement.querySelector('aside');
    expect(aside.classList.contains('w-full')).toBe(true);
    expect(aside.classList.contains('w-80')).toBe(false);
    expect(fixture.nativeElement.querySelector('section')).toBeNull();

    fixture.nativeElement.querySelector('[data-testid="select-article-1"]').click();
    httpMock.expectOne('/api/tenants/7/articles/1').flush({
      id: 1,
      title: 'Handbook',
      text: 'Extracted text',
      status: 'READY',
      failureReason: null,
      originalFileUrl: 'https://example.com/handbook.pdf',
    });
    fixture.detectChanges();

    expect(aside.classList.contains('w-full')).toBe(false);
    expect(aside.classList.contains('w-80')).toBe(true);
    expect(fixture.nativeElement.querySelector('section')).toBeTruthy();
  });

  it('hides upload/edit/delete controls when the corresponding permission is missing', () => {
    fixture.detectChanges();
    flushSetup([{ id: 1, title: 'Handbook', status: 'READY' }], ['ARTICLE_VIEW']);

    expect(fixture.nativeElement.querySelector('[data-testid="upload-form"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="delete-article-1"]')).toBeNull();

    fixture.nativeElement.querySelector('[data-testid="select-article-1"]').click();
    httpMock.expectOne('/api/tenants/7/articles/1').flush({
      id: 1,
      title: 'Handbook',
      text: 'Text',
      status: 'READY',
      failureReason: null,
      originalFileUrl: 'https://example.com/handbook.pdf',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="edit-form"]')).toBeNull();
  });
});
