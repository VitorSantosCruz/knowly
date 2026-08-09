import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { RenameFormComponent } from './rename-form.component';

describe('RenameFormComponent', () => {
  let fixture: ComponentFixture<RenameFormComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RenameFormComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(RenameFormComponent);
    fixture.componentRef.setInput('initialTitle', 'Old name');
    fixture.componentRef.setInput('initialIcon', 'STAR');
    fixture.detectChanges();
  });

  it('prefills the name input and icon picker with the current title/icon', () => {
    const nameInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="rename-form-name-input"]',
    );
    expect(nameInput.value).toBe('Old name');
    expect(
      fixture.nativeElement
        .querySelector('[data-testid="icon-picker-option-STAR"]')
        .getAttribute('aria-pressed'),
    ).toBe('true');
  });

  it('emits saved with the new title/icon', () => {
    const nameInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="rename-form-name-input"]',
    );
    nameInput.value = 'New name';
    nameInput.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('[data-testid="icon-picker-option-ROCKET"]').click();
    fixture.detectChanges();

    let emitted: { title: string; icon: string | null } | undefined;
    fixture.componentInstance.saved.subscribe((v) => (emitted = v));
    fixture.nativeElement.querySelector('[data-testid="rename-form-save"]').click();

    expect(emitted).toEqual({ title: 'New name', icon: 'ROCKET' });
  });

  it('emits cancelled on cancel', () => {
    let cancelled = false;
    fixture.componentInstance.cancelled.subscribe(() => (cancelled = true));
    fixture.nativeElement.querySelector('[data-testid="rename-form-cancel"]').click();
    expect(cancelled).toBe(true);
  });

  it('disables save until the name is non-blank', () => {
    const nameInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="rename-form-name-input"]',
    );
    nameInput.value = '   ';
    nameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="rename-form-save"]').disabled).toBe(
      true,
    );
  });

  it('renders the same generic error text regardless of which status code caused it', () => {
    fixture.componentRef.setInput('error', true);
    fixture.detectChanges();
    const message = fixture.nativeElement.querySelector(
      '[data-testid="rename-form-error"]',
    ).textContent;

    fixture.componentRef.setInput('error', false);
    fixture.componentRef.setInput('error', true);
    fixture.detectChanges();
    const message2 = fixture.nativeElement.querySelector(
      '[data-testid="rename-form-error"]',
    ).textContent;

    expect(message).toBe(message2);
  });
});
