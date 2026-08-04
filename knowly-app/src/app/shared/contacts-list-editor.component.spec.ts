import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormArray, FormGroup } from '@angular/forms';
import { provideTransloco } from '@jsverse/transloco';
import { ContactsListEditorComponent, createContactGroup } from './contacts-list-editor.component';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('ContactsListEditorComponent', () => {
  let fixture: ComponentFixture<ContactsListEditorComponent>;
  let formArray: FormArray<FormGroup>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContactsListEditorComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ContactsListEditorComponent);
    formArray = new FormArray<FormGroup>([createContactGroup()]);
    fixture.componentRef.setInput('formArray', formArray);
    fixture.detectChanges();
  });

  it('starts with one empty row', () => {
    expect(formArray.length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('[data-testid^="contacts-row-"]').length).toBe(1);
  });

  it("includes isPrimary: true in every row so ContactDto's primitive boolean never gets a null", () => {
    expect(formArray.at(0).value).toEqual({ type: 'EMAIL', value: '', isPrimary: true });

    fixture.nativeElement
      .querySelector('[data-testid="contacts-add-row"]')
      .dispatchEvent(new Event('click'));

    expect(formArray.at(1).value.isPrimary).toBe(true);
  });

  it('selects the <option> matching a non-first (pre-set) contact type on first render', () => {
    // Regression test: same [value]-vs-[selected] <select> bug already fixed elsewhere in the
    // app (see profile-fields-form.component.ts's countryCode <select> "Bugfix" comments) — EMAIL
    // being the first contactTypes entry meant the default new-row case never exposed it here.
    // Must be seeded as PHONE *before* the first detectChanges(), not patched afterward, to
    // reproduce the actual bug (Angular fails to apply [value] only on the very first CD pass,
    // before the @for-generated <option>s exist yet).
    const phoneGroup = createContactGroup();
    phoneGroup.patchValue({ type: 'PHONE', value: '+5511987654321' });
    const freshArray = new FormArray<FormGroup>([phoneGroup]);

    const freshFixture = TestBed.createComponent(ContactsListEditorComponent);
    freshFixture.componentRef.setInput('formArray', freshArray);
    freshFixture.detectChanges();

    const typeSelect = freshFixture.nativeElement.querySelector(
      '[data-testid="contacts-type-0"]',
    ) as HTMLSelectElement;
    expect(typeSelect.value).toBe('PHONE');
  });

  it('appends a control pair when add-row is clicked', () => {
    fixture.nativeElement
      .querySelector('[data-testid="contacts-add-row"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(formArray.length).toBe(2);
  });

  it('removes a row when remove-row is clicked', () => {
    fixture.nativeElement
      .querySelector('[data-testid="contacts-add-row"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();
    expect(formArray.length).toBe(2);

    fixture.nativeElement
      .querySelector('[data-testid="contacts-remove-row-0"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(formArray.length).toBe(1);
  });

  it('shows a "must have at least one" error state when submitting with zero rows', () => {
    formArray.removeAt(0);
    fixture.componentRef.setInput('showErrors', true);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="contacts-min-length-error"]'),
    ).toBeTruthy();
  });

  it('does not show the min-length error while there is at least one row', () => {
    fixture.componentRef.setInput('showErrors', true);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="contacts-min-length-error"]'),
    ).toBeFalsy();
  });

  it('binds each row to the FormArray value', () => {
    const typeSelect = fixture.nativeElement.querySelector('[data-testid="contacts-type-0"]');
    typeSelect.value = 'EMAIL';
    typeSelect.dispatchEvent(new Event('change'));

    const valueInput = fixture.nativeElement.querySelector('[data-testid="contacts-value-0"]');
    valueInput.value = 'admin@acme.test';
    valueInput.dispatchEvent(new Event('input'));

    expect(formArray.at(0).value).toEqual({
      type: 'EMAIL',
      value: 'admin@acme.test',
      isPrimary: true,
    });
  });
});
