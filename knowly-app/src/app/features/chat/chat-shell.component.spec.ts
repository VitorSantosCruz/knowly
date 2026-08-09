import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, of } from 'rxjs';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatShellComponent } from './chat-shell.component';

describe('ChatShellComponent', () => {
  let fixture: ComponentFixture<ChatShellComponent>;
  let httpMock: HttpTestingController;
  let router: Router;
  let queryParamMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let data$: BehaviorSubject<Record<string, unknown>>;

  function setup(): void {
    TestBed.resetTestingModule();
    queryParamMap$ = new BehaviorSubject(convertToParamMap({}));
    data$ = new BehaviorSubject<Record<string, unknown>>({});
    TestBed.configureTestingModule({
      imports: [ChatShellComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParamMap$,
            paramMap: of(convertToParamMap({})),
            data: data$,
            snapshot: { data: {} },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(ChatShellComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  afterEach(() => httpMock.verify());

  /** Every branch this shell can render triggers its own `ActiveTenantService.fetch()` call
   * in addition to this shell's own (`SupportPageComponent`/none for the others) — flush every
   * currently-pending `/api/tenants/active` request identically rather than assuming exactly
   * one, since that count is an implementation detail of whichever child is active. */
  function flushActiveTenant(tenantId: number | null): void {
    for (const req of httpMock.match('/api/tenants/active')) {
      if (tenantId === null) {
        req.flush(null, { status: 204, statusText: 'No Content' });
      } else {
        req.flush({ tenantId, tenantName: 'Acme', role: 'MEMBER' });
      }
    }
  }

  function flushDirectory(): void {
    httpMock.expectOne('/api/chat/conversations').flush([]);
    httpMock.expectOne((r) => r.url === '/api/chat/eligible-participants').flush([]);
    httpMock
      .expectOne((r) => r.url === '/api/chat/discoverable-groups')
      .flush({ content: [], page: 0, size: 200, totalElements: 0, totalPages: 1 });
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
  }

  function flushSupportPageBootstrap(): void {
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: [] });
    httpMock.expectOne('/api/tenants/permissions').flush({ permissions: [] });
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
    flushActiveTenant(null);
  }

  it('defaults to people and renders ChatDirectoryComponent when section is absent', () => {
    setup();
    fixture.detectChanges();
    flushActiveTenant(null);
    flushDirectory();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-chat-directory')).toBeTruthy();
    expect(
      fixture.nativeElement
        .querySelector('[data-testid="chat-sidebar-tab-people"]')
        .getAttribute('aria-current'),
    ).toBe('page');
  });

  it('renders SupportPageComponent for section=support', () => {
    setup();
    queryParamMap$.next(convertToParamMap({ section: 'support' }));
    fixture.detectChanges();
    flushActiveTenant(null);
    flushSupportPageBootstrap();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-support-page')).toBeTruthy();
  });

  it('renders ConversationsPageComponent for section=articles with an active tenant, and the no-tenant state without one', () => {
    setup();
    queryParamMap$.next(convertToParamMap({ section: 'articles' }));
    fixture.detectChanges();
    flushActiveTenant(1);
    fixture.detectChanges();
    flushActiveTenant(1);
    httpMock.expectOne((r) => r.url === '/api/tenants/1/conversations').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-conversations-page')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="no-active-tenant-state"]'),
    ).toBeNull();
  });

  it('renders the no-active-tenant state for section=articles with no active tenant (regression, dropped route guard)', () => {
    setup();
    queryParamMap$.next(convertToParamMap({ section: 'articles' }));
    fixture.detectChanges();
    flushActiveTenant(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-conversations-page')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[data-testid="no-active-tenant-state"]'),
    ).toBeTruthy();
  });

  it('renders ConversationDetailComponent when the route data marks this a peer conversation', () => {
    setup();
    data$.next({ chatSection: 'peer' });
    fixture.detectChanges();
    flushActiveTenant(null);
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/0')
      .flush(null, { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne((r) => r.url === '/api/chat/conversations/0/messages')
      .flush(null, { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-conversation-detail')).toBeTruthy();
  });

  it('clicking a sidebar tab navigates via the Router with the section query param, not a full reload', () => {
    setup();
    fixture.detectChanges();
    flushActiveTenant(null);
    flushDirectory();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="chat-sidebar-tab-support"]').click();

    expect(router.navigate).toHaveBeenCalledWith(['/chat'], {
      queryParams: { section: 'support' },
    });
  });
});
