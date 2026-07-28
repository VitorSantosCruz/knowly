import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { StaffDirectoryPageComponent } from './staff-directory-page.component';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('StaffDirectoryPageComponent', () => {
  let fixture: ComponentFixture<StaffDirectoryPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StaffDirectoryPageComponent],
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

    fixture = TestBed.createComponent(StaffDirectoryPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders the staff user list once it resolves', () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 1, email: 'staffer@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('staffer@example.com');
    expect(fixture.nativeElement.textContent).toContain('STAFF');
  });

  it('entering a search term calls list(email) and refreshes the list', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/staff/users').flush([]);
    fixture.detectChanges();

    const searchInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-search-email"]',
    );
    searchInput.value = 'bob';
    searchInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users?email=bob')
      .flush([{ id: 2, email: 'bob@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('bob@example.com');
  });

  it('submitting the create form calls create() and refreshes the list on success', () => {
    const permissionsService = TestBed.inject(GlobalPermissionsService);
    permissionsService.fetch();
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: ['STAFF_USER_CREATE'] });

    fixture.detectChanges();
    httpMock.expectOne('/api/staff/users').flush([]);
    fixture.detectChanges();

    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="create-staff-user-email"]',
    );
    emailInput.value = 'new@example.com';
    emailInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="create-staff-user-form"]',
    );
    form.dispatchEvent(new Event('submit'));

    httpMock.expectOne('/api/staff/users').flush({});
    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 3, email: 'new@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('new@example.com');
  });

  it('hides the create form when the viewer is neither staff-admin nor STAFF_USER_CREATE-holding', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/staff/users').flush([]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="create-staff-user-form"]'),
    ).toBeFalsy();
  });

  it('shows a permission-denied state on a 403 from the list', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/staff/users')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it('selecting a staff user shows the detail panel', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 1, email: 'staffer@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    const row: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="select-staff-user-1"]',
    );
    row.click();
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush({
      userId: 1,
      email: 'staffer@example.com',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
    });
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-user-detail-panel"]'),
    ).toBeTruthy();
  });
});
