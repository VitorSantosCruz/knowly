import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { AvatarUploadComponent } from './avatar-upload.component';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('AvatarUploadComponent', () => {
  let fixture: ComponentFixture<AvatarUploadComponent>;

  async function createFixture(avatarUrl: string | null = null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AvatarUploadComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AvatarUploadComponent);
    fixture.componentRef.setInput('avatarUrl', avatarUrl);
    fixture.detectChanges();
  }

  it('renders the given avatarUrl as an image', async () => {
    await createFixture('https://example.com/avatar.png');

    const img = fixture.nativeElement.querySelector('[data-testid="avatar-upload-image"]');
    expect(img.src).toBe('https://example.com/avatar.png');
    expect(
      fixture.nativeElement.querySelector('[data-testid="avatar-upload-placeholder"]'),
    ).toBeNull();
  });

  it('renders a placeholder when avatarUrl is null', async () => {
    await createFixture(null);

    expect(
      fixture.nativeElement.querySelector('[data-testid="avatar-upload-placeholder"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="avatar-upload-image"]')).toBeNull();
  });

  it('selecting a file emits fileSelected with that File', async () => {
    await createFixture();

    const emitted: File[] = [];
    fixture.componentInstance.fileSelected.subscribe((file: File) => emitted.push(file));

    const file = new File(['content'], 'avatar.png', { type: 'image/png' });
    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-upload-input"]',
    );

    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change'));

    expect(emitted).toEqual([file]);
  });
});
