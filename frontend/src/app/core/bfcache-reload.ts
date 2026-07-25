/**
 * When a browser restores a page from the back/forward cache (e.g. the user hits "back" right
 * after logging out), it repaints the last in-memory DOM snapshot without re-running any
 * navigation/guard logic — so a logged-out user can briefly see the previous, still-authenticated
 * screen. Forcing a full reload on a bfcache restore re-runs the app from scratch, so route guards
 * and the in-memory logged-in signal are re-evaluated against the real (now invalidated) session.
 */
export function installBfcacheReload(location: Location = window.location): void {
  window.addEventListener('pageshow', (event) => {
    if ((event as PageTransitionEvent).persisted) {
      location.reload();
    }
  });
}
