import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { CreateConversationDialogComponent } from './create-conversation-dialog.component';

describe('CreateConversationDialogComponent', () => {
  let fixture: ComponentFixture<CreateConversationDialogComponent>;
  let httpMock: HttpTestingController;
  let router: Router;
  let activeTenantService: ActiveTenantService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CreateConversationDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(CreateConversationDialogComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    activeTenantService = TestBed.inject(ActiveTenantService);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  function primeActiveTenant(tenantId = 1): void {
    activeTenantService.fetch();
    httpMock
      .expectOne('/api/tenants/active')
      .flush({ tenantId, tenantName: 'Acme', role: 'MEMBER_ADMIN' });
  }

  function nameInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid="create-conversation-name-input"]');
  }
  function submitButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="create-conversation-submit"]');
  }

  it('disables submit until a non-blank name is entered (icon optional)', () => {
    primeActiveTenant();
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    expect(submitButton().disabled).toBe(true);

    nameInput().value = 'Artigos de RH';
    nameInput().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(submitButton().disabled).toBe(false);
  });

  it('submits ConversationService.create(tenantId, title, icon) and opens the new conversation on success', () => {
    primeActiveTenant();
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    nameInput().value = 'Artigos de RH';
    nameInput().dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('[data-testid="icon-picker-option-BOOK_OPEN"]').click();
    fixture.detectChanges();

    submitButton().click();

    const req = httpMock.expectOne('/api/tenants/1/conversations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ title: 'Artigos de RH', icon: 'BOOK_OPEN' });
    req.flush({ id: 9, title: 'Artigos de RH', icon: 'BOOK_OPEN' });

    expect(router.navigate).toHaveBeenCalledWith(['/chat/articles', 9]);
  });

  it('shows an inline error and keeps the dialog open with entered name/icon intact on failure', () => {
    primeActiveTenant();
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    nameInput().value = 'Artigos de RH';
    nameInput().dispatchEvent(new Event('input'));
    fixture.detectChanges();

    submitButton().click();
    httpMock
      .expectOne('/api/tenants/1/conversations')
      .flush(null, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="create-conversation-error"]'),
    ).toBeTruthy();
    expect(nameInput().value).toBe('Artigos de RH');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
