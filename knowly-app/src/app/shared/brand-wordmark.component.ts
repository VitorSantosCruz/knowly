import { Component, input } from '@angular/core';
import { BrandMarkComponent } from './brand-mark.component';

/**
 * The knowly lockup: the icon mark (see brand-mark.component.ts) followed by
 * "nowly." — the icon itself reads as the leading "K", so the text spells
 * out only the rest of the word rather than repeating it. The `compact`
 * variant drops the text entirely (just the icon) for spaces too narrow for
 * the full lockup — namely the collapsed sidebar rail (nav-menu.component.ts).
 * The text renders as inline SVG `<text>` so the loaded Fraunces webfont
 * still applies via the `.font-display` class, with the trailing dot always
 * in `--color-signal-500`. Both the icon and text inherit `currentColor`
 * from the host, so callers control light/dark contrast via the `class`
 * they pass in.
 */
@Component({
  selector: 'app-brand-wordmark',
  imports: [BrandMarkComponent],
  template: `
    @if (compact()) {
      <app-brand-mark [class]="extraClass()" [heightClass]="heightClass()" />
    } @else {
      <span [class]="'inline-flex items-center gap-1 ' + extraClass()" aria-label="knowly.">
        <app-brand-mark [heightClass]="heightClass()" />
        <svg
          [class]="heightClass() + ' w-auto align-middle'"
          viewBox="0 0 92 28"
          role="img"
          aria-hidden="true"
        >
          <text
            x="0"
            y="21"
            class="font-display"
            font-size="23"
            font-weight="600"
            letter-spacing="-0.01em"
            fill="currentColor"
          >
            nowly
            <tspan fill="var(--color-signal-500)">.</tspan>
          </text>
        </svg>
      </span>
    }
  `,
})
export class BrandWordmarkComponent {
  /** Extra classes applied to the root element — use to set text color per call site. */
  readonly class = input<string>('');
  /** Renders just the icon mark instead of the full lockup — for containers too narrow for it. */
  readonly compact = input<boolean>(false);
  /** Height utility class — see BrandMarkComponent's own doc comment for why this isn't in `class`. */
  readonly heightClass = input<string>('h-6');

  protected extraClass(): string {
    return this.class();
  }
}
