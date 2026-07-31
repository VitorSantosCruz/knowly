import { Component, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { DisplayMessage } from '../../core/chat.model';
import { MessageComposerComponent } from './message-composer.component';
import { MessageThreadComponent } from './message-thread.component';

@Component({
  selector: 'app-host',
  imports: [MessageThreadComponent],
  template: `
    <app-message-thread
      [messages]="messages()"
      [hasMore]="hasMore()"
      [loading]="loading()"
      [loadError]="loadError()"
      [showComposer]="showComposer()"
      (loadMore)="loadMoreCount = loadMoreCount + 1"
      (send)="sent = $event"
      (retry)="retried = $event"
    />
  `,
})
class HostComponent {
  messages = signal<DisplayMessage[]>([]);
  hasMore = signal(false);
  loading = signal(false);
  loadError = signal(false);
  showComposer = signal(false);
  loadMoreCount = 0;
  sent: string | undefined;
  retried: DisplayMessage | undefined;
}

describe('MessageThreadComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
  });

  const msg = (over: Partial<DisplayMessage> = {}): DisplayMessage => ({
    id: 1,
    senderUserId: 2,
    senderNickname: 'Bob',
    content: 'hi',
    createdAt: 'now',
    ...over,
  });

  it('renders messages oldest-to-newest with sender and content', () => {
    fixture.componentInstance.messages.set([
      msg({ id: 1, senderNickname: 'A' }),
      msg({ id: 2, senderNickname: 'B' }),
    ]);
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('[data-testid="message-thread-item"]');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('A');
    expect(items[1].textContent).toContain('B');
  });

  it('shows load-more only when hasMore is true, and emits loadMore on click', () => {
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="message-thread-load-more"]'),
    ).toBeNull();

    fixture.componentInstance.hasMore.set(true);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="message-thread-load-more"]',
    );
    expect(button).toBeTruthy();
    expect(button.getAttribute('aria-label')).toBeTruthy();
    button.click();
    expect(fixture.componentInstance.loadMoreCount).toBe(1);
  });

  it('shows a local loading indicator without hiding already-rendered messages', () => {
    fixture.componentInstance.messages.set([msg()]);
    fixture.componentInstance.loading.set(true);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="message-thread-loading"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelectorAll('[data-testid="message-thread-item"]').length,
    ).toBe(1);
  });

  it('shows a retry control on loadError without hiding already-loaded messages, and emits loadMore', () => {
    fixture.componentInstance.messages.set([msg()]);
    fixture.componentInstance.loadError.set(true);
    fixture.detectChanges();

    const retry: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="message-thread-retry"]',
    );
    expect(retry).toBeTruthy();
    expect(retry.getAttribute('aria-label')).toBeTruthy();
    retry.click();
    expect(fixture.componentInstance.loadMoreCount).toBe(1);
    expect(
      fixture.nativeElement.querySelectorAll('[data-testid="message-thread-item"]').length,
    ).toBe(1);
  });

  it('renders the composer only when showComposer is true, wiring its send output', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="message-composer"]')).toBeNull();

    fixture.componentInstance.showComposer.set(true);
    fixture.detectChanges();

    const composer = fixture.debugElement.query(By.directive(MessageComposerComponent))
      .componentInstance as MessageComposerComponent;
    composer.send.emit('hey');

    expect(fixture.componentInstance.sent).toBe('hey');
  });

  it('renders a pending message distinctly from a failed one, with a retry action for failed', () => {
    fixture.componentInstance.messages.set([
      msg({ id: 1, sendState: 'pending' }),
      msg({ id: 2, sendState: 'failed' }),
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="message-pending"]')).toBeTruthy();
    const failedRetry: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="message-retry"]',
    );
    expect(failedRetry).toBeTruthy();
    expect(failedRetry.getAttribute('aria-label')).toBeTruthy();

    failedRetry.click();
    expect(fixture.componentInstance.retried?.id).toBe(2);
  });
});
