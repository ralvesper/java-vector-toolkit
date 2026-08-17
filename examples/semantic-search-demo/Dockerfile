FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Build from the monorepo root because the demo module depends on sibling modules.
COPY . .
RUN mvn -q -pl examples/semantic-search-demo -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/examples/semantic-search-demo/target/semantic-search-demo-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

