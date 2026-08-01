import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
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
  });

  function dialog(): HTMLDialogElement {
    return fixture.nativeElement.querySelector('dialog');
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

  it('emits (confirm) when the confirm button is clicked', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const confirmed = vi.fn();
    fixture.componentInstance.confirm.subscribe(confirmed);

    fixture.nativeElement.querySelector('[data-testid="confirm-dialog-confirm"]').click();

    expect(confirmed).toHaveBeenCalledOnce();
  });

  it('emits (cancel) when the cancel button is clicked', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const cancelled = vi.fn();
    fixture.componentInstance.cancel.subscribe(cancelled);

    fixture.nativeElement.querySelector('[data-testid="confirm-dialog-cancel"]').click();

    expect(cancelled).toHaveBeenCalledOnce();
  });

  it('emits (cancel) on the native dialog cancel event (Escape)', () => {
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const cancelled = vi.fn();
    fixture.componentInstance.cancel.subscribe(cancelled);

    dialog().dispatchEvent(new Event('cancel'));

    expect(cancelled).toHaveBeenCalledOnce();
  });
});
