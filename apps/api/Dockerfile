FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY apps/api/pom.xml pom.xml
COPY infra/maven/settings.xml /root/.m2/settings.xml
COPY apps/api/src src
RUN --mount=type=cache,target=/root/.m2/repository mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 app && adduser -D -u 10001 -G app app
WORKDIR /app
COPY --from=build /build/target/transfervio-api-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8091
ENTRYPOINT ["java","-jar","/app/app.jar"]
