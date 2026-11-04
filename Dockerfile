# ---- Build stage: compile and package the fat jar with the Maven wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the build descriptors first so the dependency layer is cached
# until pom.xml changes, not on every source edit.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build.
RUN ./mvnw -B -q package -DskipTests

# ---- Runtime stage: JRE only, non-root ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 ledger
COPY --from=build /workspace/target/wallet-ledger-api-*.jar app.jar
USER ledger
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
