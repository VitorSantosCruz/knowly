import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { PersonInfoModalComponent } from './person-info-modal.component';

describe('PersonInfoModalComponent', () => {
  let fixture: ComponentFixture<PersonInfoModalComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PersonInfoModalComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(PersonInfoModalComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('is closed by default and opens the native <dialog> when open() is true', () => {
    fixture.detectChanges();

    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector(
      '[data-testid="person-info-modal"]',
    );
    expect(dialog.open).toBe(false);

    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('userId', 2);
    fixture.detectChanges();

    expect(dialog.open).toBe(true);
    httpMock.expectOne('/api/users/2/profile').flush({
      userId: 2,
      email: 'bob@x.com',
      fields: { fullName: 'Bob' },
      avatarUrl: null,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Bob');
  });

  it('emits dismissed when the close button is clicked', () => {
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('userId', 2);
    fixture.detectChanges();
    httpMock.expectOne('/api/users/2/profile').flush({
      userId: 2,
      email: 'bob@x.com',
      fields: { fullName: 'Bob' },
      avatarUrl: null,
    });
    fixture.detectChanges();

    let dismissed = false;
    fixture.componentInstance.dismissed.subscribe(() => (dismissed = true));
    fixture.nativeElement.querySelector('[data-testid="person-info-modal-close"]').click();

    expect(dismissed).toBe(true);
  });
});
