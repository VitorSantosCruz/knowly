import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InputMaskDirective, InputMaskType } from './input-mask.directive';

@Component({
  standalone: true,
  imports: [InputMaskDirective],
  template: `<input
    data-testid="masked-input"
    type="text"
    [appInputMask]="mask()"
    [appInputMaskCountry]="country()"
    (appInputMaskChange)="onChange($event)"
  />`,
})
class HostComponent {
  readonly mask = signal<InputMaskType>('taxId');
  readonly country = signal<string | null>('BR');
  emitted: string[] = [];

  onChange(value: string): void {
    this.emitted.push(value);
  }
}

describe('InputMaskDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;
  let el: HTMLInputElement;

  async function createFixture(mask: InputMaskType, country: string | null = 'BR'): Promise<void> {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    host.mask.set(mask);
    host.country.set(country);
    fixture.detectChanges();
    el = fixture.nativeElement.querySelector('[data-testid="masked-input"]');
  }

  function typeInto(value: string, caret?: number): void {
    el.value = value;
    if (caret !== undefined) {
      el.setSelectionRange(caret, caret);
    }
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('formats digits as CPF (000.000.000-00) for country BR (regression)', async () => {
    await createFixture('taxId', 'BR');

    typeInto('1');
    expect(el.value).toBe('1');

    typeInto('12');
    expect(el.value).toBe('12');

    typeInto('123456789');
    expect(el.value).toBe('123.456.789');

    typeInto('12345678900');
    expect(el.value).toBe('123.456.789-00');
  });

  it('formats digits as CEP (00000-000) for country BR', async () => {
    await createFixture('postalCode', 'BR');

    typeInto('01310100');
    expect(el.value).toBe('01310-100');
  });

  it('formats an 11-digit phone number as (00) 00000-0000 for country BR', async () => {
    await createFixture('phone', 'BR');

    typeInto('11987654321');
    expect(el.value).toBe('(11) 98765-4321');
  });

  it('formats a 10-digit phone number as (00) 0000-0000 for country BR', async () => {
    await createFixture('phone', 'BR');

    typeInto('1132654321');
    expect(el.value).toBe('(11) 3265-4321');
  });

  it('emits the unmasked, digits-only value on every keystroke regardless of the mask', async () => {
    await createFixture('taxId', 'BR');

    typeInto('123');
    expect(host.emitted.at(-1)).toBe('123');

    typeInto('123.4');
    expect(host.emitted.at(-1)).toBe('1234');

    typeInto('123.456.789-00');
    expect(host.emitted.at(-1)).toBe('12345678900');
  });

  it('preserves the caret position when deleting a character mid-string', async () => {
    await createFixture('taxId', 'BR');

    typeInto('123.456.789-00');
    expect(el.value).toBe('123.456.789-00');

    // Simulate deleting the '4' right after the first dot (caret lands at index 4 post-delete,
    // i.e. right after 3 digits were typed — the directive re-locates the caret to sit after
    // the same digit count in the reformatted string, not jumped to the end).
    typeInto('123.56.789-00', 4);
    expect(el.value).toBe('123.567.890-0');
    expect(el.selectionStart).toBe(3);
  });

  it('stops reformatting once the mask input changes away from a masked type', async () => {
    await createFixture('phone', 'BR');

    typeInto('11987654321');
    expect(el.value).toBe('(11) 98765-4321');

    host.mask.set('taxId');
    fixture.detectChanges();

    typeInto('12345678900');
    expect(el.value).toBe('123.456.789-00');
  });

  it('passes the raw value through unmodified for a country with no known mask (GB + postalCode)', async () => {
    await createFixture('postalCode', 'GB');

    typeInto('EC1A 1BB');
    expect(el.value).toBe('EC1A 1BB');
    expect(host.emitted.at(-1)).toBe('EC1A 1BB');
  });

  it('formats digits as SSN (000-00-0000) for country US', async () => {
    await createFixture('taxId', 'US');

    typeInto('123456789');
    expect(el.value).toBe('123-45-6789');
  });
});
