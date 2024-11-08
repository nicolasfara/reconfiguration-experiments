# Generate a gradle dockerfile for executing this project
FROM eclipse-temurin:23

WORKDIR /app
COPY . .

RUN ./gradlew tasks

ENTRYPOINT ["./gradlew", "-Pbatch=true", "runAllLoadBased"]
