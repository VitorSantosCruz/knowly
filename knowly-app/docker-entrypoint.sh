#!/bin/sh
set -eu

envsubst '${TURNSTILE_SITE_KEY}' \
  < /usr/share/nginx/html/config.template.json \
  > /usr/share/nginx/html/config.json

exec "$@"
