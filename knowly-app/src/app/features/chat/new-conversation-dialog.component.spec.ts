import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { NewConversationDialogComponent } from './new-conversation-dialog.component';

describe('NewConversationDialogComponent', () => {
  let fixture: ComponentFixture<NewConversationDialogComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NewConversationDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(NewConversationDialogComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  it('in 1:1 mode fetches direct candidates and creates a DIRECT conversation on select+create', () => {
    fixture.detectChanges();
    httpMock
      .expectOne(
        (r) => r.url === '/api/chat/eligible-participants' && r.params.get('scope') === 'direct',
      )
      .flush([{ userId: 2, nickname: 'Bob' }]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="participant-picker-candidate"]')
      .dispatchEvent(new Event('change'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="create-button"]').click();

    const req = httpMock.expectOne('/api/chat/conversations');
    expect(req.request.body).toEqual({ kind: 'DIRECT', participantUserIds: [2] });
    req.flush({
      id: 9,
      kind: 'PEER_DIRECT',
      tenantId: null,
      title: null,
      participantUserIds: [1, 2],
    });
  });

  it('in member-only-group mode fetches group candidates for the chosen tenant and creates a GROUP conversation with tenantId', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.params.get('scope') === 'direct').flush([]);

    fixture.nativeElement.querySelector('[data-testid="mode-member-group"]').click();
    fixture.detectChanges();

    const tenantInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="tenant-id-input"]',
    );
    tenantInput.value = '7';
    tenantInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/chat/eligible-participants' &&
          r.params.get('scope') === 'group' &&
          r.params.get('tenantId') === '7',
      )
      .flush([{ userId: 3, nickname: 'Member' }]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="participant-picker-candidate"]')
      .dispatchEvent(new Event('change'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="create-button"]').click();

    const req = httpMock.expectOne('/api/chat/conversations');
    expect(req.request.body).toEqual({ kind: 'GROUP', tenantId: 7, participantUserIds: [3] });
    req.flush({ id: 10, kind: 'PEER_GROUP', tenantId: 7, title: null, participantUserIds: [1, 3] });
  });

  it('in staff-only-group mode fetches staff-only candidates and creates a GROUP conversation with tenantId null', () => {
    fixture.detectChanges();
    httpMock.expectOne((r) => r.params.get('scope') === 'direct').flush([]);

    fixture.nativeElement.querySelector('[data-testid="mode-staff-group"]').click();
    fixture.detectChanges();
    httpMock
      .expectOne(
        (r) =>
          r.url === '/api/chat/eligible-participants' &&
          r.params.get('scope') === 'group-staff-only',
      )
      .flush([{ userId: 4, nickname: 'Staffer' }]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="participant-picker-candidate"]')
      .dispatchEvent(new Event('change'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="create-button"]').click();

    const req = httpMock.expectOne('/api/chat/conversations');
    expect(req.request.body).toEqual({ kind: 'GROUP', tenantId: null, participantUserIds: [4] });
    req.flush({
      id: 11,
      kind: 'PEER_GROUP',
      tenantId: null,
      title: null,
      participantUserIds: [1, 4],
    });
  });

  it('shows an inline rejection on a 400/403 and does not navigate away', () => {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.params.get('scope') === 'direct')
      .flush([{ userId: 2, nickname: 'Bob' }]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="participant-picker-candidate"]')
      .dispatchEvent(new Event('change'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="create-button"]').click();

    httpMock
      .expectOne('/api/chat/conversations')
      .flush('ineligible', { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="new-conversation-error"]'),
    ).toBeTruthy();
    expect(router.navigate).not.toHaveBeenCalledWith(['/chat', expect.anything()]);
  });
});
