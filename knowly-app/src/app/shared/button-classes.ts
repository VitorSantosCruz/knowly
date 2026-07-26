// Shared Tailwind class helper for native `<button>` elements, replacing
// PrimeNG's `ButtonDirective` (`pButton`/`severity`/`text`). See
// specify/features/primeng-removal/PLAN.md — this is a pure CSS-class
// helper, not a component wrapper, since every previous usage was just
// styling on a native element with no host-binding/behavior beyond that.

export type ButtonVariant = 'primary' | 'secondary' | 'danger';

export interface ButtonClassOptions {
  /** Ghost/text style — no solid background, matches PrimeNG's `text` attribute. */
  ghost?: boolean;
  /** Icon-only circular button — matches PrimeNG's `rounded` attribute. */
  rounded?: boolean;
}

const BASE =
  'inline-flex items-center justify-center gap-1.5 text-sm font-medium transition-colors duration-fast ease-fluid disabled:pointer-events-none disabled:opacity-50';

const SOLID: Record<ButtonVariant, string> = {
  primary: 'bg-signal-600 text-white hover:bg-signal-500',
  secondary:
    'bg-ink-100 text-ink-900 hover:bg-ink-200 dark:bg-ink-800 dark:text-white dark:hover:bg-ink-700',
  danger: 'bg-red-600 text-white hover:bg-red-500',
};

const GHOST: Record<ButtonVariant, string> = {
  primary: 'text-signal-600 hover:bg-signal-50 dark:text-signal-400 dark:hover:bg-signal-950/30',
  secondary: 'text-ink-500 hover:bg-ink-100 dark:text-ink-400 dark:hover:bg-ink-800',
  danger: 'text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/30',
};

function shapeClass(rounded: boolean): string {
  return rounded ? 'rounded-full' : 'rounded-lg';
}

function paddingClass(ghost: boolean, rounded: boolean): string {
  if (rounded) {
    return 'p-2';
  }
  return ghost ? 'px-3 py-1.5' : 'px-4 py-2';
}

export function buttonClass(
  variant: ButtonVariant = 'primary',
  options: ButtonClassOptions = {},
): string {
  const { ghost = false, rounded = false } = options;
  const variantClass = ghost ? GHOST[variant] : SOLID[variant];

  return [BASE, shapeClass(rounded), paddingClass(ghost, rounded), variantClass].join(' ');
}
