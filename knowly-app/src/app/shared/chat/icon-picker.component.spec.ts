import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ICON_KEYS } from '../../core/chat.model';
import { IconPickerComponent } from './icon-picker.component';

describe('IconPickerComponent', () => {
  let fixture: ComponentFixture<IconPickerComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [IconPickerComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(IconPickerComponent);
  });

  it('renders all 24 icon buttons, each with a distinct, human-readable aria-label', () => {
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('[data-testid^="icon-picker-option-"]'),
    );
    expect(buttons.length).toBe(ICON_KEYS.length);

    const labels = buttons.map((btn) => btn.getAttribute('aria-label'));
    expect(new Set(labels).size).toBe(ICON_KEYS.length);
    for (const key of ICON_KEYS) {
      const btn = fixture.nativeElement.querySelector(`[data-testid="icon-picker-option-${key}"]`);
      expect(btn).toBeTruthy();
      expect(btn.getAttribute('aria-label')).not.toBe(key);
      expect(btn.getAttribute('aria-label')?.length).toBeGreaterThan(0);
    }
  });

  it('clicking an icon emits iconSelected with that key', () => {
    fixture.detectChanges();
    const emitted: string[] = [];
    fixture.componentInstance.iconSelected.subscribe((key) => emitted.push(key));

    fixture.nativeElement.querySelector('[data-testid="icon-picker-option-ROCKET"]').click();

    expect(emitted).toEqual(['ROCKET']);
  });

  it('marks the currently-selected key as visually distinguished (aria-pressed)', () => {
    fixture.componentRef.setInput('selected', 'STAR');
    fixture.detectChanges();

    const selectedBtn = fixture.nativeElement.querySelector(
      '[data-testid="icon-picker-option-STAR"]',
    );
    const otherBtn = fixture.nativeElement.querySelector(
      '[data-testid="icon-picker-option-HEART"]',
    );
    expect(selectedBtn.getAttribute('aria-pressed')).toBe('true');
    expect(otherBtn.getAttribute('aria-pressed')).toBe('false');
  });

  it('renders no icon as selected when [selected]="null"', () => {
    fixture.componentRef.setInput('selected', null);
    fixture.detectChanges();

    const pressed = fixture.nativeElement.querySelectorAll('[aria-pressed="true"]');
    expect(pressed.length).toBe(0);
  });

  it('behaves identically when [selected] is left unset', () => {
    fixture.detectChanges();

    const pressed = fixture.nativeElement.querySelectorAll('[aria-pressed="true"]');
    expect(pressed.length).toBe(0);
  });

  it('emits only IconKey literal values drawn from ICON_KEYS, never a constructed string', () => {
    fixture.detectChanges();
    const emitted: string[] = [];
    fixture.componentInstance.iconSelected.subscribe((key) => emitted.push(key));

    for (const key of ICON_KEYS) {
      fixture.nativeElement.querySelector(`[data-testid="icon-picker-option-${key}"]`).click();
    }

    expect(emitted).toEqual(ICON_KEYS);
  });
});
