import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { MemberSupportChannelComponent } from './member-support-channel.component';

describe('MemberSupportChannelComponent', () => {
  let fixture: ComponentFixture<MemberSupportChannelComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MemberSupportChannelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(MemberSupportChannelComponent);
    fixture.componentRef.setInput('tenantId', 1);
    fixture.componentRef.setInput('memberUserId', 9);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows a "start support ticket" action when the channel has never been opened (404)', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/support/members/9/channel')
      .flush('nf', { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/9/channel/messages')
      .flush('nf', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="start-ticket-button"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="message-thread"]')).toBeNull();
  });

  it('renders the thread instead of only the start action once a channel exists', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/support/members/9/channel')
      .flush({
        id: 1,
        kind: 'SUPPORT',
        tenantId: 1,
        title: null,
        participantUserIds: [9],
        participantNicknames: { 9: 'Nick' },
      });
    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/9/channel/messages')
      .flush({ messages: [], nextCursor: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-thread"]')).toBeTruthy();
  });

  it('clicking start ticket calls openTicket and a 409 shows an inline message instead of crashing', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/support/members/9/channel')
      .flush('nf', { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/9/channel/messages')
      .flush('nf', { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="start-ticket-button"]').click();
    httpMock
      .expectOne('/api/tenants/1/support/tickets')
      .flush('conflict', { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="open-ticket-error"]')).toBeTruthy();
  });
});
