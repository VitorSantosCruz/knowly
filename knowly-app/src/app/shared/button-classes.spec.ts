import { buttonClass } from './button-classes';

describe('buttonClass', () => {
  it('defaults to a solid primary button', () => {
    const result = buttonClass();

    expect(result).toContain('bg-signal-600');
    expect(result).toContain('text-white');
    expect(result).toContain('rounded-lg');
  });

  it('returns a solid secondary variant', () => {
    const result = buttonClass('secondary');

    expect(result).toContain('bg-ink-100');
    expect(result).not.toContain('bg-signal-600');
  });

  it('returns a solid danger variant', () => {
    const result = buttonClass('danger');

    expect(result).toContain('bg-red-600');
    expect(result).toContain('text-white');
  });

  it('returns a ghost (text) secondary variant with no solid background', () => {
    const result = buttonClass('secondary', { ghost: true });

    expect(result).toContain('text-ink-500');
    expect(result).not.toContain('bg-ink-100 text-ink-900');
  });

  it('returns a ghost danger variant', () => {
    const result = buttonClass('danger', { ghost: true });

    expect(result).toContain('text-red-600');
    expect(result).not.toContain('bg-red-600');
  });

  it('returns a rounded, square-padded variant for icon-only buttons', () => {
    const result = buttonClass('secondary', { ghost: true, rounded: true });

    expect(result).toContain('rounded-full');
    expect(result).toContain('p-2');
    expect(result).not.toContain('rounded-lg');
  });
});
