import { installBfcacheReload } from './bfcache-reload';

describe('installBfcacheReload', () => {
  afterEach(() => {
    window.removeEventListener('pageshow', () => {
      /* noop */
    });
  });

  it('reloads the page when restored from the back/forward cache', () => {
    const reload = vi.fn();
    installBfcacheReload({ reload } as unknown as Location);

    const event = new Event('pageshow');
    Object.defineProperty(event, 'persisted', { value: true });
    window.dispatchEvent(event);

    expect(reload).toHaveBeenCalled();
  });

  it('does nothing on a normal (non-bfcache) pageshow', () => {
    const reload = vi.fn();
    installBfcacheReload({ reload } as unknown as Location);

    const event = new Event('pageshow');
    Object.defineProperty(event, 'persisted', { value: false });
    window.dispatchEvent(event);

    expect(reload).not.toHaveBeenCalled();
  });
});
