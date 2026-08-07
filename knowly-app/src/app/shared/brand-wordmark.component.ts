import { Component, input } from '@angular/core';

/**
 * The "knowly." wordmark logotype, plus a `compact` variant ("k.") for
 * spaces too narrow for the full mark (namely the collapsed sidebar rail —
 * see nav-menu.component.ts). Renders as inline SVG `<text>` so the loaded
 * Fraunces webfont still applies via the `.font-display` class, with the
 * trailing dot always in `--color-signal-500`. Text color for the "knowly"/
 * "k" part is inherited from the host's `currentColor`, so callers control
 * light/dark contrast via the `class` they pass in.
 */
@Component({
  selector: 'app-brand-wordmark',
  template: `
    @if (compact()) {
      <svg
        viewBox="0 0 22 28"
        [class]="'h-6 w-auto align-middle ' + extraClass()"
        role="img"
        aria-label="knowly."
      >
        <text
          x="0"
          y="21"
          class="font-display"
          font-size="23"
          font-weight="600"
          fill="currentColor"
        >
          k
          <tspan fill="var(--color-signal-500)">.</tspan>
        </text>
      </svg>
    } @else {
      <svg
        viewBox="0 0 118 28"
        [class]="'h-6 w-auto align-middle ' + extraClass()"
        role="img"
        aria-label="knowly."
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
          knowly
          <tspan fill="var(--color-signal-500)">.</tspan>
        </text>
      </svg>
    }
  `,
})
export class BrandWordmarkComponent {
  /** Extra classes applied to the root <svg> — use to set text color per call site. */
  readonly class = input<string>('');
  /** Renders "k." instead of "knowly." — for containers too narrow for the full wordmark. */
  readonly compact = input<boolean>(false);

  protected extraClass(): string {
    return this.class();
  }
}
