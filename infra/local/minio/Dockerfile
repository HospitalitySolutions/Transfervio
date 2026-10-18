# MinIO community images were withdrawn. Build the official, pinned source.
FROM golang:1.25-alpine AS build
RUN apk add --no-cache git
RUN CGO_ENABLED=0 GOBIN=/out go install github.com/minio/minio@RELEASE.2025-10-15T17-29-55Z
FROM alpine:3.22
RUN apk add --no-cache ca-certificates curl && addgroup -g 10001 minio && adduser -D -u 10001 -G minio minio
COPY --from=build /out/minio /usr/local/bin/minio
RUN mkdir /data && chown minio:minio /data
USER minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
