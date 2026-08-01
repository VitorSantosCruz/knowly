import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { Subject, of, throwError } from 'rxjs';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('ConfirmDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.componentRef.setInput('fetchToken', () => of('correct-horse'));
  });

  function dialog(): HTMLDialogElement {
    return fixture.nativeElement.querySelector('dialog');
  }

  function input(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid="confirm-dialog-input"]');
  }

  function confirmButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="confirm-dialog-confirm"]');
  }

  function type(value: string): void {
    const el = input();
    el.value = value;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('calls showModal() when open becomes true and close() when it becomes false', () => {
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    expect(dialog().open).toBe(false);

    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    expect(dialog().open).toBe(true);

    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    expect(dialog().open).toBe(false);
  });

  it('renders the message input as text', () => {
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('message', "Delete 'Handbook'?");
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("Delete 'Handbook'?");
  });

  it('emits (dismissed) when the cancel button is clicked', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const dismissed = vi.fn();
    fixture.componentInstance.dismissed.subscribe(dismissed);

    fixture.nativeElement.querySelector('[data-testid="confirm-dialog-cancel"]').click();

    expect(dismissed).toHaveBeenCalledOnce();
  });

  it('emits (dismissed) on the native dialog cancel event (Escape)', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const dismissed = vi.fn();
    fixture.componentInstance.dismissed.subscribe(dismissed);

    dialog().dispatchEvent(new Event('cancel'));

    expect(dismissed).toHaveBeenCalledOnce();
  });

  it('fetches and displays the word when the dialog opens (REQ-1/2)', () => {
    const fetchToken = vi.fn().mockReturnValue(of('correct-horse'));
    fixture.componentRef.setInput('fetchToken', fetchToken);

    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    expect(fetchToken).toHaveBeenCalledOnce();
    expect(
      fixture.nativeElement.querySelector('[data-testid="confirm-dialog-word"]').textContent,
    ).toContain('correct-horse');
  });

  it('keeps Confirm disabled until the typed text exactly matches (REQ-3/4)', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    expect(confirmButton().disabled).toBe(true);

    type('wrong');
    expect(confirmButton().disabled).toBe(true);

    type('correct-horse');
    expect(confirmButton().disabled).toBe(false);
  });

  it('emits (confirm) with the matched word (REQ-5)', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    type('correct-horse');

    const confirmed = vi.fn();
    fixture.componentInstance.confirm.subscribe(confirmed);

    confirmButton().click();

    expect(confirmed).toHaveBeenCalledWith('correct-horse');
  });

  it('shows a loading state and keeps Confirm disabled while the token request is in flight (REQ-6)', () => {
    const subject = new Subject<string>();
    fixture.componentRef.setInput('fetchToken', () => subject.asObservable());

    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="confirm-dialog-loading"]'),
    ).toBeTruthy();
    expect(confirmButton().disabled).toBe(true);

    subject.next('correct-horse');
    subject.complete();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="confirm-dialog-loading"]'),
    ).toBeNull();
  });

  it('shows an error with a retry affordance if fetching the token fails (REQ-7)', () => {
    const fetchToken = vi
      .fn()
      .mockReturnValueOnce(throwError(() => new Error('boom')))
      .mockReturnValueOnce(of('correct-horse'));
    fixture.componentRef.setInput('fetchToken', fetchToken);

    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="confirm-dialog-fetch-error"]'),
    ).toBeTruthy();
    expect(confirmButton().disabled).toBe(true);

    fixture.nativeElement.querySelector('[data-testid="confirm-dialog-retry"]').click();
    fixture.detectChanges();

    expect(fetchToken).toHaveBeenCalledTimes(2);
    expect(
      fixture.nativeElement.querySelector('[data-testid="confirm-dialog-word"]').textContent,
    ).toContain('correct-horse');
  });

  it('discards the stale word/input, shows the REQ-8 message, and re-fetches when retryToken bumps', () => {
    const fetchToken = vi
      .fn()
      .mockReturnValueOnce(of('correct-horse'))
      .mockReturnValueOnce(of('fresh-word'));
    fixture.componentRef.setInput('fetchToken', fetchToken);
    fixture.componentRef.setInput('retryToken', 0);

    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    type('correct-horse');
    expect(confirmButton().disabled).toBe(false);

    fixture.componentRef.setInput('retryToken', 1);
    fixture.detectChanges();

    expect(fetchToken).toHaveBeenCalledTimes(2);
    expect(
      fixture.nativeElement.querySelector('[data-testid="confirm-dialog-invalid-word"]'),
    ).toBeTruthy();
    expect(confirmButton().disabled).toBe(true);
    expect(input().value).toBe('');
    expect(
      fixture.nativeElement.querySelector('[data-testid="confirm-dialog-word"]').textContent,
    ).toContain('fresh-word');
  });

  it('discards word/typed on dismiss without calling anything else (REQ-9/21)', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    type('correct-horse');

    fixture.nativeElement.querySelector('[data-testid="confirm-dialog-cancel"]').click();
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();

    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    expect(input().value).toBe('');
  });

  it('blocks paste via ClipboardEvent and does not treat the pasted text as a match (REQ-22)', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const el = input();
    const pasteEvent = new Event('paste', { cancelable: true }) as ClipboardEvent;
    el.dispatchEvent(pasteEvent);
    fixture.detectChanges();

    expect(pasteEvent.defaultPrevented).toBe(true);
    expect(el.value).toBe('');
    expect(confirmButton().disabled).toBe(true);
  });

  it('blocks drop via DragEvent (REQ-22)', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const el = input();
    const dropEvent = new Event('drop', { cancelable: true }) as DragEvent;
    el.dispatchEvent(dropEvent);
    fixture.detectChanges();

    expect(dropEvent.defaultPrevented).toBe(true);
    expect(el.value).toBe('');
  });

  it('blocks dragover so drop is reachable (REQ-22)', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const el = input();
    const dragOverEvent = new Event('dragover', { cancelable: true });
    el.dispatchEvent(dragOverEvent);

    expect(dragOverEvent.defaultPrevented).toBe(true);
  });

  it('still updates the typed signal normally via manual (input) events after paste-blocking is wired', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    type('correct-horse');

    expect(confirmButton().disabled).toBe(false);
  });

  it('never logs the word to the console', () => {
    const source = ConfirmDialogComponent.toString();
    expect(source).not.toMatch(/console\.(log|error|warn)/);
  });
});
