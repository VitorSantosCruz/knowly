import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { SupportPageComponent } from './support-page.component';

describe('SupportPageComponent', () => {
  let fixture: ComponentFixture<SupportPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [SupportPageComponent],
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
    fixture = TestBed.createComponent(SupportPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  function flushBaseline(options: {
    globalPermissions?: string[];
    permissions?: string[];
    memberships?: { tenantId: number; tenantName: string; role: string; active: boolean }[];
  }) {
    httpMock
      .expectOne('/api/staff/permissions')
      .flush({ permissions: options.globalPermissions ?? [] });
    httpMock
      .expectOne('/api/tenants/permissions')
      .flush({ permissions: options.permissions ?? [] });
    httpMock.expectOne('/api/tenants/memberships').flush(options.memberships ?? []);
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
  }

  it('fetches GET /api/staff/permissions once and renders the staff inbox when STAFF_SUPPORT_HANDLE is held', () => {
    fixture.detectChanges();
    flushBaseline({
      globalPermissions: ['STAFF_SUPPORT_HANDLE'],
      memberships: [{ tenantId: 1, tenantName: 'T', role: 'MEMBER', active: true }],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="staff-support-inbox"]')).toBeTruthy();
    httpMock.expectOne('/api/tenants?page=0&size=100').flush({
      content: [{ id: 1, name: 'T' }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    httpMock.expectOne('/api/tenants/1/support/tickets/unclaimed').flush([]);
  });

  it('absent STAFF_SUPPORT_HANDLE, renders MemberSupportBrowseComponent alongside the own channel when SUPPORT_CHANNEL_VIEW is held', () => {
    fixture.detectChanges();
    flushBaseline({
      permissions: ['SUPPORT_CHANNEL_VIEW'],
      memberships: [{ tenantId: 1, tenantName: 'T', role: 'MEMBER', active: true }],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="browse-member-id-input"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="staff-support-inbox"]')).toBeNull();

    httpMock.expectOne('/api/tenants/1/support/members/1/channel').flush('nf', {
      status: 404,
      statusText: 'Not Found',
    });
    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/1/channel/messages')
      .flush('nf', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="member-support-channel"]'),
    ).toBeTruthy();
  });

  it('absent both permissions, renders MemberSupportChannelComponent only', () => {
    fixture.detectChanges();
    flushBaseline({
      memberships: [{ tenantId: 1, tenantName: 'T', role: 'MEMBER', active: true }],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="staff-support-inbox"]')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="browse-member-id-input"]'),
    ).toBeNull();

    httpMock.expectOne('/api/tenants/1/support/members/1/channel').flush('nf', {
      status: 404,
      statusText: 'Not Found',
    });
    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/1/channel/messages')
      .flush('nf', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="member-support-channel"]'),
    ).toBeTruthy();
  });

  it('for a pure-staff session with no tenant membership, renders the empty state rather than erroring', () => {
    fixture.detectChanges();
    flushBaseline({ memberships: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="support-empty-state"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="member-support-channel"]'),
    ).toBeNull();
  });

  it('polls the member channel via a visibility-gated interval(5000), mirroring peer-chat polling', () => {
    fixture.detectChanges();
    flushBaseline({
      memberships: [{ tenantId: 1, tenantName: 'T', role: 'MEMBER', active: true }],
    });
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/1/support/members/1/channel').flush('nf', {
      status: 404,
      statusText: 'Not Found',
    });
    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/1/channel/messages')
      .flush('nf', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    Object.defineProperty(document, 'visibilityState', {
      value: 'visible',
      configurable: true,
    });

    vi.advanceTimersByTime(5000);

    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/1/channel/messages')
      .flush({ messages: [], nextCursor: null });
  });
});
