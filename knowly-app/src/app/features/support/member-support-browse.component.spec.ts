import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { MemberSupportBrowseComponent } from './member-support-browse.component';

describe('MemberSupportBrowseComponent', () => {
  let fixture: ComponentFixture<MemberSupportBrowseComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MemberSupportBrowseComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(MemberSupportBrowseComponent);
    fixture.componentRef.setInput('tenantId', 1);
    fixture.componentRef.setInput('memberUserId', 9);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the chosen member channel read-only', () => {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/9/channel/messages')
      .flush({
        messages: [
          { id: 1, senderUserId: 9, senderNickname: 'Nick', content: 'hi', createdAt: '' },
        ],
        nextCursor: null,
      });
    fixture.detectChanges();

    const thread = fixture.nativeElement.querySelector('[data-testid="message-thread"]');
    expect(thread).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="message-composer"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeNull();
  });

  it('renders the existing no-access state on a 403 instead of a partial view', () => {
    fixture.detectChanges();
    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/9/channel/messages')
      .flush('forbidden', { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="message-thread"]')).toBeNull();
  });
});
