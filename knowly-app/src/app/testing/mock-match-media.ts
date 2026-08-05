export function mockMatchMedia(prefersDark = false): void {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: (query: string) => ({
      matches: query === '(prefers-color-scheme: dark)' && prefersDark,
      media: query,
      addEventListener: () => {
        /* noop */
      },
      removeEventListener: () => {
        /* noop */
      },
    }),
  });
}

export interface FakeMediaQueryList {
  matches: boolean;
  media: string;
  addEventListener: (type: string, listener: (ev: { matches: boolean }) => void) => void;
  removeEventListener: (type: string, listener: (ev: { matches: boolean }) => void) => void;
  dispatch: (matches: boolean) => void;
}

/**
 * jsdom (this repo's Vitest environment) doesn't implement `window.matchMedia` at all — used by
 * `SidebarStateService`'s `viewportIsDesktop` breakpoint check, so any spec that transitively
 * constructs it (`nav-menu.component.spec.ts`, `app-shell.component.spec.ts`,
 * `sidebar-state.service.spec.ts`) needs this mocked before the first `TestBed.createComponent`/
 * `TestBed.inject`, unlike `mockMatchMedia` above (fixed to the dark-mode query only, no `change`
 * dispatch). Returns the fake `MediaQueryList` so a test can simulate a viewport change via
 * `.dispatch(matches)`.
 */
export function mockViewportMatchMedia(initialMatches: boolean): FakeMediaQueryList {
  let listener: ((ev: { matches: boolean }) => void) | null = null;

  const mql: FakeMediaQueryList = {
    matches: initialMatches,
    media: '(min-width: 768px)',
    addEventListener: (_type, fn) => {
      listener = fn;
    },
    removeEventListener: () => {
      listener = null;
    },
    dispatch: (matches: boolean) => {
      mql.matches = matches;
      listener?.({ matches });
    },
  };

  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: () => mql,
  });

  return mql;
}
