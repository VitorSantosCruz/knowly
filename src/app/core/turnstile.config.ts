// Cloudflare Turnstile site keys are public by design (embedded in client-side markup),
// unlike the secret key, which stays server-side (see knowly's TURNSTILE_SECRET_KEY).
// Replace this placeholder with the real site key before deploying to any environment
// where CAPTCHA is expected to actually render.
export const TURNSTILE_SITE_KEY = '';
