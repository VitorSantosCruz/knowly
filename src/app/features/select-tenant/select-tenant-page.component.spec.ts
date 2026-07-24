import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { SelectTenantPageComponent } from './select-tenant-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('SelectTenantPageComponent', () => {
  let fixture: ComponentFixture<SelectTenantPageComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SelectTenantPageComponent],
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

    fixture = TestBed.createComponent(SelectTenantPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists the memberships to choose from', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/memberships').flush([
      { tenantId: 1, tenantName: 'Acme', role: 'ADMIN', active: false },
      { tenantId: 2, tenantName: 'Other Co', role: 'MEMBER', active: false },
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Acme');
    expect(fixture.nativeElement.textContent).toContain('Other Co');
  });

  it('selecting a tenant posts the choice and navigates to the dashboard', () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/memberships').flush([
      { tenantId: 1, tenantName: 'Acme', role: 'ADMIN', active: false },
      { tenantId: 2, tenantName: 'Other Co', role: 'MEMBER', active: false },
    ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="select-tenant-2"]').click();

    const req = httpMock.expectOne('/api/tenants/active');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ tenantId: 2 });
    req.flush({});

    expect(navigateSpy).toHaveBeenCalledWith('/dashboard');
  });
});
