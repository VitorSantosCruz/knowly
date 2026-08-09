import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { CreateGroupDialogComponent } from './create-group-dialog.component';

describe('CreateGroupDialogComponent', () => {
  let fixture: ComponentFixture<CreateGroupDialogComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CreateGroupDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(CreateGroupDialogComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  function nameInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid="create-group-name-input"]');
  }
  function submitButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="create-group-submit"]');
  }
  function visibilityRadio(value: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(`[data-testid="create-group-visibility-${value}"]`);
  }
  function selectVisibility(value: string): void {
    const el = visibilityRadio(value);
    el.checked = true;
    el.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('disables submit until both a non-empty name and a visibility option are set', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    expect(submitButton().disabled).toBe(true);

    nameInput().value = 'Grupo A';
    nameInput().dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(submitButton().disabled).toBe(true);

    selectVisibility('PUBLIC');
    expect(submitButton().disabled).toBe(false);
  });

  it('submits POST /api/chat/conversations with kind GROUP + visibility and navigates on 201', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    nameInput().value = 'Grupo A';
    nameInput().dispatchEvent(new Event('input'));
    selectVisibility('REQUEST_TO_JOIN');
    fixture.detectChanges();

    submitButton().click();

    const req = httpMock.expectOne('/api/chat/conversations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(
      expect.objectContaining({
        kind: 'GROUP',
        title: 'Grupo A',
        visibility: 'REQUEST_TO_JOIN',
        participantUserIds: [],
      }),
    );
    req.flush({
      id: 42,
      kind: 'PEER_GROUP',
      tenantId: null,
      title: 'Grupo A',
      participantUserIds: [1],
    });

    expect(router.navigate).toHaveBeenCalledWith(['/chat', 42]);
  });

  it('shows an inline error and keeps the dialog open with entered data on failure', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    nameInput().value = 'Grupo A';
    nameInput().dispatchEvent(new Event('input'));
    selectVisibility('PUBLIC');

    submitButton().click();
    httpMock
      .expectOne('/api/chat/conversations')
      .flush(null, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="create-group-error"]')).toBeTruthy();
    expect(nameInput().value).toBe('Grupo A');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
