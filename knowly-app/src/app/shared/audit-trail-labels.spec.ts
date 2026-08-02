import { TestBed } from '@angular/core/testing';
import { TranslocoService, provideTransloco } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';
import { translateAuditAction } from './audit-trail-labels';

describe('translateAuditAction', () => {
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

  it('translates a known action', () => {
    const transloco = TestBed.inject(TranslocoService);

    expect(translateAuditAction('staff.user.demote', transloco)).toBe('Demoted a staff user');
  });

  it('translates a known tenant member action', () => {
    const transloco = TestBed.inject(TranslocoService);

    expect(translateAuditAction('tenant.member.promote', transloco)).toBe(
      'Promoted a tenant member',
    );
  });

  it('falls back to the raw action string when unknown', () => {
    const transloco = TestBed.inject(TranslocoService);

    expect(translateAuditAction('some.unknown.action', transloco)).toBe('some.unknown.action');
  });
});
