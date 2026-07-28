import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { ProfileFieldsFormComponent } from './profile-fields-form.component';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';
import { ProfileFields } from '../core/profile.service';

describe('ProfileFieldsFormComponent', () => {
  let fixture: ComponentFixture<ProfileFieldsFormComponent>;

  const fields: ProfileFields = {
    fullName: 'Jane Doe',
    address: '123 Main St',
    rg: '11.111.111-1',
    cpf: '111.111.111-11',
    phone: '+15550000',
  };

  async function createFixture(disabled = false): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ProfileFieldsFormComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileFieldsFormComponent);
    fixture.componentRef.setInput('fields', fields);
    fixture.componentRef.setInput('disabled', disabled);
    fixture.detectChanges();
  }

  function input(testId: string): HTMLInputElement {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  it('renders the five editable fields and never an email input', async () => {
    await createFixture();

    expect(input('profile-field-fullName').value).toBe('Jane Doe');
    expect(input('profile-field-address').value).toBe('123 Main St');
    expect(input('profile-field-rg').value).toBe('11.111.111-1');
    expect(input('profile-field-cpf').value).toBe('111.111.111-11');
    expect(input('profile-field-phone').value).toBe('+15550000');
    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-email"]')).toBeNull();
  });

  it('emits submitted with the entered values, never including email', async () => {
    await createFixture();

    const emitted: unknown[] = [];
    fixture.componentInstance.submitted.subscribe((value: ProfileFields) => emitted.push(value));

    input('profile-field-fullName').value = 'Jane Smith';
    input('profile-field-fullName').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-form"]').dispatchEvent(
      new Event('submit', { cancelable: true }),
    );

    expect(emitted).toEqual([{ ...fields, fullName: 'Jane Smith' }]);
    expect(emitted[0]).not.toHaveProperty('email');
  });

  it('prevents submission/emits nothing when disabled', async () => {
    await createFixture(true);

    const emitted: unknown[] = [];
    fixture.componentInstance.submitted.subscribe((value: ProfileFields) => emitted.push(value));

    fixture.nativeElement.querySelector('[data-testid="profile-fields-form"]').dispatchEvent(
      new Event('submit', { cancelable: true }),
    );

    expect(emitted).toEqual([]);
    expect(input('profile-field-fullName').disabled).toBe(true);
  });
});
