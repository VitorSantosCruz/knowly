# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -DskipTests package && \
    java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:25-jre-alpine AS runtime
RUN addgroup -S knowly && adduser -S knowly -G knowly
WORKDIR /app
COPY --from=build --chown=knowly:knowly /app/target/extracted/dependencies/ ./
COPY --from=build --chown=knowly:knowly /app/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=knowly:knowly /app/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=knowly:knowly /app/target/extracted/application/ ./
USER knowly
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
