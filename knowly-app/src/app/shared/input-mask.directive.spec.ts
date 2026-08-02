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
    (appInputMaskChange)="onChange($event)"
  />`,
})
class HostComponent {
  readonly mask = signal<InputMaskType>('cpf');
  emitted: string[] = [];

  onChange(value: string): void {
    this.emitted.push(value);
  }
}

describe('InputMaskDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;
  let el: HTMLInputElement;

  async function createFixture(mask: InputMaskType): Promise<void> {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    host.mask.set(mask);
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

  it('formats digits as CPF (000.000.000-00) as the user types', async () => {
    await createFixture('cpf');

    typeInto('1');
    expect(el.value).toBe('1');

    typeInto('12');
    expect(el.value).toBe('12');

    typeInto('123456789');
    expect(el.value).toBe('123.456.789');

    typeInto('12345678900');
    expect(el.value).toBe('123.456.789-00');
  });

  it('formats digits as CEP (00000-000)', async () => {
    await createFixture('cep');

    typeInto('01310100');
    expect(el.value).toBe('01310-100');
  });

  it('formats an 11-digit phone number as (00) 00000-0000', async () => {
    await createFixture('phone');

    typeInto('11987654321');
    expect(el.value).toBe('(11) 98765-4321');
  });

  it('formats a 10-digit phone number as (00) 0000-0000', async () => {
    await createFixture('phone');

    typeInto('1132654321');
    expect(el.value).toBe('(11) 3265-4321');
  });

  it('emits the unmasked, digits-only value on every keystroke regardless of the mask', async () => {
    await createFixture('cpf');

    typeInto('123');
    expect(host.emitted.at(-1)).toBe('123');

    typeInto('123.4');
    expect(host.emitted.at(-1)).toBe('1234');

    typeInto('123.456.789-00');
    expect(host.emitted.at(-1)).toBe('12345678900');
  });

  it('preserves the caret position when deleting a character mid-string', async () => {
    await createFixture('cpf');

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
    await createFixture('phone');

    typeInto('11987654321');
    expect(el.value).toBe('(11) 98765-4321');

    host.mask.set('cpf');
    fixture.detectChanges();

    typeInto('12345678900');
    expect(el.value).toBe('123.456.789-00');
  });
});
