FROM node:24-alpine AS build
WORKDIR /build
COPY apps/web/package*.json ./
RUN npm ci --no-audit --no-fund
COPY apps/web/ ./
RUN npm run build
FROM nginx:1.28-alpine
COPY infra/local/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
