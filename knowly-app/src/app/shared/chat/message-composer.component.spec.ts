import { Component, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { MessageComposerComponent } from './message-composer.component';

@Component({
  selector: 'app-host',
  imports: [MessageComposerComponent],
  template: `<app-message-composer (send)="onSend($event)" />`,
})
class HostComponent {
  readonly composer = viewChild.required(MessageComposerComponent);
  sent: string[] = [];
  onSend(content: string): void {
    this.sent.push(content);
  }
}

describe('MessageComposerComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideTransloco({
          config: { availableLangs: ['en'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('exposes an aria-label on the send control', () => {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="message-composer-send"]',
    );
    expect(button.getAttribute('aria-label')).toBeTruthy();
  });

  it('emits send with the composer text and clears it', async () => {
    const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector(
      '[data-testid="message-composer-input"]',
    );
    textarea.value = 'hello there';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();

    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="message-composer"]',
    );
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.sent).toEqual(['hello there']);
    expect(textarea.value).toBe('');
  });

  it('does not emit for blank content', () => {
    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="message-composer"]',
    );
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance.sent).toEqual([]);
  });
});
