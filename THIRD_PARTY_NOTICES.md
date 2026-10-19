# Third-party components

The MIT license covers original Transfervio source only. Dependency licenses remain their own. Maven coordinates are in `apps/api/pom.xml`; frontend dependencies are locked in `apps/web/package-lock.json`; image versions and MinIO source release are in the Dockerfiles/Compose files.

The separately running [MinIO server](https://github.com/minio/minio/tree/RELEASE.2025-10-15T17-29-55Z) is AGPL-3.0 licensed. Its image is built from that unmodified, pinned source release. Review upstream license, source availability and maintenance requirements before distributing or deploying it. No third-party software is relicensed by Transfervio's LICENSE.
