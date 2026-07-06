const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js';

export function loadTurnstileScript(): void {
  if (document.querySelector(`script[src="${SCRIPT_SRC}"]`)) {
    return;
  }

  const script = document.createElement('script');
  script.src = SCRIPT_SRC;
  script.async = true;
  script.defer = true;
  document.head.appendChild(script);
}
