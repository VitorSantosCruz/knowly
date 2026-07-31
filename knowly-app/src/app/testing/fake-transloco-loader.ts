import { Injectable } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';
import { of } from 'rxjs';
import en from '../../../public/i18n/en.json';
import ptBR from '../../../public/i18n/pt-BR.json';

const DICTIONARIES: ReadonlyMap<string, Translation> = new Map([
  ['en', en],
  ['pt-BR', ptBR],
]);

@Injectable()
export class FakeTranslocoLoader implements TranslocoLoader {
  getTranslation(langPath: string) {
    return of(DICTIONARIES.get(langPath) ?? {});
  }
}
