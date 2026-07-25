# syntax=docker/dockerfile:1

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.31-alpine AS runtime
RUN apk add --no-cache gettext && \
    addgroup -S knowly && adduser -S knowly -G knowly && \
    chown -R knowly:knowly /var/cache/nginx /var/run /usr/share/nginx/html && \
    touch /var/run/nginx.pid && chown knowly:knowly /var/run/nginx.pid
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --chown=knowly:knowly docker-entrypoint.sh /docker-entrypoint.sh
COPY --from=build --chown=knowly:knowly /app/dist/knowly-app/browser/ /usr/share/nginx/html/
USER knowly
EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
