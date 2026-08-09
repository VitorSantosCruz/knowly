import { TestBed } from '@angular/core/testing';
import { TranslocoService, provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';
import { translatePermissionDescription, translatePermissionLabel } from './permission-labels';

describe('translatePermissionLabel', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });

    await firstValueFrom(TestBed.inject(TranslocoService).load('en'));
  });

  it('translates a known GlobalPermission enum value', () => {
    const transloco = TestBed.inject(TranslocoService);

    expect(translatePermissionLabel('STAFF_USER_VIEW', transloco)).toBe('View staff users');
  });

  it('translates a known Permission enum value', () => {
    const transloco = TestBed.inject(TranslocoService);

    expect(translatePermissionLabel('ARTICLE_EDIT', transloco)).toBe('Edit articles');
  });

  it('falls back to the raw value when no translation key exists', () => {
    const transloco = TestBed.inject(TranslocoService);

    expect(translatePermissionLabel('SOME_UNKNOWN_PERMISSION', transloco)).toBe(
      'SOME_UNKNOWN_PERMISSION',
    );
  });
});

describe('translatePermissionDescription', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });

    await firstValueFrom(TestBed.inject(TranslocoService).load('en'));
  });

  it('translates a known permission value description', () => {
    const transloco = TestBed.inject(TranslocoService);

    expect(translatePermissionDescription('STAFF_USER_VIEW', transloco)).toBe(
      'View the list and detail of staff user accounts.',
    );
  });

  it('falls back to the raw value when no description translation key exists', () => {
    const transloco = TestBed.inject(TranslocoService);

    expect(translatePermissionDescription('SOME_UNKNOWN_PERMISSION', transloco)).toBe(
      'SOME_UNKNOWN_PERMISSION',
    );
  });
});
