import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from './fake-transloco-loader';

export function createMetricWidgetHarness<T>(componentType: Type<T>) {
  TestBed.configureTestingModule({
    imports: [componentType],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideTransloco({
        config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
        loader: FakeTranslocoLoader,
      }),
    ],
  });

  const fixture: ComponentFixture<T> = TestBed.createComponent(componentType);
  const httpMock = TestBed.inject(HttpTestingController);

  return { fixture, httpMock };
}
