import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { MembersPageComponent } from './members-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('MembersPageComponent', () => {
  let fixture: ComponentFixture<MembersPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MembersPageComponent],
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

    fixture = TestBed.createComponent(MembersPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushActiveTenant() {
    httpMock
      .expectOne('/api/tenants/memberships')
      .flush([{ tenantId: 7, tenantName: 'Acme', role: 'ADMIN', active: true }]);
  }

  it('renders the member list once the active tenant and members resolve', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('a@example.com');
  });

  it('adding a member refreshes the list', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/7/members').flush([]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="add-member-email"]',
    );
    emailInput.value = 'new@example.com';
    emailInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="add-member-form"]',
    );
    form.dispatchEvent(new Event('submit'));

    httpMock.expectOne('/api/tenants/7/members').flush({});
    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 2, email: 'new@example.com', role: 'MEMBER' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('new@example.com');
  });

  it('removing a member removes it from the list', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const removeButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="remove-member-1"]',
    );
    removeButton.click();

    httpMock.expectOne('/api/tenants/7/members/1').flush({});
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('a@example.com');
  });

  it('selecting a member shows the detail panel', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const row: HTMLElement = fixture.nativeElement.querySelector('[data-testid="select-member-1"]');
    row.click();
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/7/members/1').flush({
      membershipId: 1,
      email: 'a@example.com',
      role: 'MEMBER',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
    });
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="member-detail-panel"]')).toBeTruthy();
  });

  it('shows a permission-denied state when the members list is forbidden', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });
});
