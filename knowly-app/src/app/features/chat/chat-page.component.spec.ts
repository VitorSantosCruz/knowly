import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ChatPageComponent } from './chat-page.component';

describe('ChatPageComponent', () => {
  let fixture: ComponentFixture<ChatPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(ChatPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('never renders a permission-denied state for the base conversations list', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/chat/conversations').flush([]);
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'me@x.com', fields: {}, avatarUrl: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="conversation-list"]')).toBeTruthy();
  });
});
