# syntax=docker/dockerfile:1

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.31-alpine AS runtime
RUN addgroup -S knowly && adduser -S knowly -G knowly && \
    chown -R knowly:knowly /var/cache/nginx /var/run /usr/share/nginx/html && \
    touch /var/run/nginx.pid && chown knowly:knowly /var/run/nginx.pid
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=knowly:knowly /app/dist/knowly-app/browser/ /usr/share/nginx/html/
USER knowly
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
