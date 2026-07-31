import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { SupportService } from '../../core/support.service';
import { StaffSupportChannelComponent } from './staff-support-channel.component';

describe('StaffSupportChannelComponent', () => {
  let fixture: ComponentFixture<StaffSupportChannelComponent>;
  let httpMock: HttpTestingController;
  let supportService: SupportService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [StaffSupportChannelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(StaffSupportChannelComponent);
    fixture.componentRef.setInput('tenantId', 1);
    fixture.componentRef.setInput('memberUserId', 9);
    fixture.componentRef.setInput('currentUserId', 42);
    httpMock = TestBed.inject(HttpTestingController);
    supportService = TestBed.inject(SupportService);
  });

  afterEach(() => httpMock.verify());

  function flushMessages() {
    httpMock
      .expectOne((r) => r.url === '/api/tenants/1/support/members/9/channel/messages')
      .flush({ messages: [], nextCursor: null });
  }

  it('loads the full channel history via openChannel, not just the ticket messages', () => {
    fixture.detectChanges();
    flushMessages();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-thread"]')).toBeTruthy();
  });

  it('hides the composer when the ticket is assigned to someone else', () => {
    supportService['_activeTicket'].set({
      id: 1,
      supportChannelId: 1,
      status: 'ASSIGNED',
      assignedStaffUserId: 999,
      openedAt: 'now',
      closedAt: null,
    });
    fixture.detectChanges();
    flushMessages();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-composer"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('read-only');
  });

  it('shows the composer for the assigned staff user, and hides it after transfer', () => {
    supportService['_activeTicket'].set({
      id: 1,
      supportChannelId: 1,
      status: 'ASSIGNED',
      assignedStaffUserId: 42,
      openedAt: 'now',
      closedAt: null,
    });
    fixture.detectChanges();
    flushMessages();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-composer"]')).toBeTruthy();

    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="transfer-target-input"]',
    );
    input.value = '77';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="transfer-button"]').click();

    httpMock.expectOne('/api/tenants/1/support/tickets/1/transfer').flush({
      id: 1,
      supportChannelId: 1,
      status: 'ASSIGNED',
      assignedStaffUserId: 77,
      openedAt: 'now',
      closedAt: null,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-composer"]')).toBeNull();
  });

  it('closing shows a closed badge, removes the composer, and shows no reopen action', () => {
    supportService['_activeTicket'].set({
      id: 1,
      supportChannelId: 1,
      status: 'ASSIGNED',
      assignedStaffUserId: 42,
      openedAt: 'now',
      closedAt: null,
    });
    fixture.detectChanges();
    flushMessages();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="close-button"]').click();
    httpMock.expectOne('/api/tenants/1/support/tickets/1/close').flush({
      id: 1,
      supportChannelId: 1,
      status: 'CLOSED',
      assignedStaffUserId: 42,
      openedAt: 'now',
      closedAt: 'later',
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement
        .querySelector('[data-testid="ticket-status-badge"]')
        .getAttribute('data-status'),
    ).toBe('CLOSED');
    expect(fixture.nativeElement.querySelector('[data-testid="message-composer"]')).toBeNull();
    expect(fixture.nativeElement.textContent.toLowerCase()).not.toContain('reopen');
  });
});
