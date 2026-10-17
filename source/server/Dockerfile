FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM build AS test

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache ffmpeg && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/english-learning-server-0.1.0.jar app.jar
RUN mkdir -p /app/data/assets && chown -R app:app /app/data
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
