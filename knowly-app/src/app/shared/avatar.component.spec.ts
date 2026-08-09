import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AvatarComponent } from './avatar.component';

describe('AvatarComponent', () => {
  let fixture: ComponentFixture<AvatarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [AvatarComponent] });
    fixture = TestBed.createComponent(AvatarComponent);
  });

  it('renders the image when avatarUrl is set', () => {
    fixture.componentRef.setInput('avatarUrl', 'https://example.com/me.png');
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('[data-testid="avatar-image"]');
    expect(img).toBeTruthy();
    expect(img.getAttribute('src')).toBe('https://example.com/me.png');
    expect(fixture.nativeElement.querySelector('[data-testid="avatar-fallback"]')).toBeNull();
  });

  it('falls back to the generic icon when avatarUrl is null', () => {
    fixture.componentRef.setInput('avatarUrl', null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="avatar-image"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="avatar-fallback"]')).toBeTruthy();
  });

  it('falls back to the generic icon if the image fails to load', () => {
    fixture.componentRef.setInput('avatarUrl', 'https://example.com/broken.png');
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="avatar-image"]')
      .dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="avatar-image"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="avatar-fallback"]')).toBeTruthy();
  });
});
