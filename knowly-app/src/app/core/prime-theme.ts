import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

/**
 * PrimeNG theme preset for knowly's "Ink & Signal" brand.
 *
 * Maps the existing Tailwind `ink-*`/`signal-*` scales (see `styles.css`'s
 * `@theme` block) onto PrimeNG's semantic design tokens via `definePreset`,
 * instead of accepting Aura's default blue/gray palette. Values are the same
 * hex stops already approved for the brand — this file has no new colors of
 * its own, it only re-expresses ones that already exist.
 *
 * `signal` (warm amber) becomes PrimeNG's `primary` palette, since primary is
 * exactly what "signal" means in this brand: the one accent for actions/
 * focus/highlights. `ink` becomes the `surface` palette, since ink already
 * carries nearly all surfaces/text.
 */
export const InkSignalPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#fdf6e9',
      100: '#faebc8',
      200: '#f4d78e',
      300: '#edbf5c',
      400: '#e2a53a',
      500: '#c98a2c',
      600: '#a86e21',
      700: '#85551d',
      800: '#63401a',
      900: '#402816',
      950: '#2b1a0f',
    },
    colorScheme: {
      light: {
        surface: {
          0: '#ffffff',
          50: '#f4f6fb',
          100: '#e6eaf5',
          200: '#c9d2e8',
          300: '#a2b0d4',
          400: '#7686b8',
          500: '#55649b',
          600: '#414c7f',
          700: '#343c66',
          800: '#232951',
          900: '#161a38',
          950: '#0d1024',
        },
        primary: {
          color: '{primary.600}',
          contrastColor: '#ffffff',
          hoverColor: '{primary.700}',
          activeColor: '{primary.800}',
        },
        highlight: {
          background: '{primary.100}',
          focusBackground: '{primary.200}',
          color: '{primary.800}',
          focusColor: '{primary.900}',
        },
      },
      dark: {
        surface: {
          0: '#ffffff',
          50: '#f4f6fb',
          100: '#e6eaf5',
          200: '#c9d2e8',
          300: '#a2b0d4',
          400: '#7686b8',
          500: '#55649b',
          600: '#414c7f',
          700: '#343c66',
          800: '#232951',
          900: '#161a38',
          950: '#0d1024',
        },
        primary: {
          color: '{primary.400}',
          contrastColor: '{surface.950}',
          hoverColor: '{primary.300}',
          activeColor: '{primary.200}',
        },
        highlight: {
          background: 'color-mix(in srgb, {primary.400}, transparent 84%)',
          focusBackground: 'color-mix(in srgb, {primary.400}, transparent 70%)',
          color: 'rgba(255, 255, 255, 0.9)',
          focusColor: 'rgba(255, 255, 255, 1)',
        },
      },
    },
  },
});
