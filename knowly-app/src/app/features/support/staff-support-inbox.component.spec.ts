import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { StaffSupportInboxComponent } from './staff-support-inbox.component';

describe('StaffSupportInboxComponent', () => {
  let fixture: ComponentFixture<StaffSupportInboxComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [StaffSupportInboxComponent],
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
    fixture = TestBed.createComponent(StaffSupportInboxComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  it('renders inboxTickets(), calling fetchInbox once per resolved tenant', () => {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/tenants')
      .flush({
        content: [{ id: 1, name: 'T1' }],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      });
    httpMock
      .expectOne('/api/tenants/1/support/tickets/unclaimed')
      .flush([
        {
          id: 5,
          supportChannelId: 5,
          status: 'OPEN',
          assignedStaffUserId: null,
          openedAt: 'now',
          closedAt: null,
        },
      ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="inbox-ticket"]').length).toBe(1);
  });

  it('clicking claim calls SupportService.claim and navigates on success', () => {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/tenants')
      .flush({
        content: [{ id: 1, name: 'T1' }],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      });
    httpMock
      .expectOne('/api/tenants/1/support/tickets/unclaimed')
      .flush([
        {
          id: 5,
          supportChannelId: 5,
          status: 'OPEN',
          assignedStaffUserId: null,
          openedAt: 'now',
          closedAt: null,
        },
      ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="claim-button"]').click();
    httpMock
      .expectOne('/api/tenants/1/support/tickets/5/claim')
      .flush({
        id: 5,
        supportChannelId: 5,
        status: 'ASSIGNED',
        assignedStaffUserId: 1,
        openedAt: 'now',
        closedAt: null,
      });

    expect(router.navigate).toHaveBeenCalledWith(['/support']);
  });
});
