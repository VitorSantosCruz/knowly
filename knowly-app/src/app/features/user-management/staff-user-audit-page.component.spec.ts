import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { of } from 'rxjs';
import { StaffUserAuditPageComponent } from './staff-user-audit-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('StaffUserAuditPageComponent', () => {
  let fixture: ComponentFixture<StaffUserAuditPageComponent>;
  let httpMock: HttpTestingController;

  async function createFixture(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [StaffUserAuditPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ userId: '7' })) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StaffUserAuditPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  const page0 = {
    content: [
      {
        occurredAt: '2026-01-01T10:00:00Z',
        action: 'STAFF_USER_CREATE',
        resourceType: 'StaffUser',
        resourceId: '7',
        tenantId: null,
        outcome: 'SUCCESS',
        metadata: null,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };

  it('reads userId from the route and renders paginated audit events', async () => {
    await createFixture();
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/staff/users/7/audit-trail?page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush(page0);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="shared-list-row-0"]')).not.toBeNull();
  });

  it('renders no row actions', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock.expectOne('/api/staff/users/7/audit-trail?page=0&size=20').flush(page0);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid^="shared-list-action-"]')).toBeNull();
  });

  it('changing page (pageChange) re-fetches the next page from the service', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/staff/users/7/audit-trail?page=0&size=20')
      .flush({ ...page0, totalPages: 2, totalElements: 21 });
    fixture.detectChanges();

    const nextButton = fixture.nativeElement.querySelector(
      '[data-testid="shared-list-next-page"]',
    ) as HTMLButtonElement;
    nextButton.click();
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/staff/users/7/audit-trail?page=1&size=20');
    expect(req.request.method).toBe('GET');
    req.flush({ ...page0, page: 1, totalPages: 2, totalElements: 21 });
  });

  it('renders the permission-denied state on a 403', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users/7/audit-trail?page=0&size=20')
      .flush({ message: 'nope' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).not.toBeNull();
  });

  it('renders the network error state on a non-403 error', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users/7/audit-trail?page=0&size=20')
      .flush({ message: 'boom' }, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).not.toBeNull();
  });
});
