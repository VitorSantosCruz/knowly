import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PhoneDdiInputComponent } from './phone-ddi-input.component';

describe('PhoneDdiInputComponent', () => {
  let fixture: ComponentFixture<PhoneDdiInputComponent>;

  async function createFixture(value = '', countryCode: string | null = 'BR'): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PhoneDdiInputComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PhoneDdiInputComponent);
    fixture.componentRef.setInput('value', value);
    fixture.componentRef.setInput('countryCode', countryCode);
    fixture.detectChanges();
  }

  function ddiInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('[data-testid^="phone-ddi-input"]');
  }

  // Bugfix (2026-08-02): the DDI prefix must render with a leading "+" (display-only, like the
  // existing REQ-21/22 masking rules) — the underlying `value`/emitted E.164 string must stay
  // unaffected.
  it('displays the default DDI with a leading "+" for a brand-new phone contact row', async () => {
    await createFixture('', 'BR');

    expect(ddiInput().value).toBe('+55');
  });

  it('displays the DDI split out of an existing E.164 value with a leading "+"', async () => {
    await createFixture('+15551234', 'US');

    expect(ddiInput().value).toBe('+1');
  });

  it('still composes/emits a plain E.164 value (no literal "+" duplication) when the DDI is edited', async () => {
    await createFixture('', 'BR');

    const emitted: string[] = [];
    fixture.componentInstance.valueChange.subscribe((value) => emitted.push(value));

    ddiInput().value = '+1';
    ddiInput().dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(emitted[emitted.length - 1]).toBe('+1');
    expect(ddiInput().value).toBe('+1');
  });
});
