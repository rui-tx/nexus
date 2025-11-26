FROM vegardit/graalvm-maven:latest-java25 as builder

WORKDIR /app

# Copy pom.xml
COPY pom.xml .

# download dependencies, this layer will be cached
RUN mvn compile -B

# copy the rest of the source code
COPY . .

# Run native compilation
RUN mvn package -Pnative -DskipTests

FROM debian:bookworm-slim

LABEL io.github.ruitx.nexus-sample-app.version="1.0.0-SNAPSHOT"

WORKDIR /app

# Install runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends     ca-certificates     && rm -rf /var/lib/apt/lists/*

# Copy the built native executable from the builder stage
COPY --from=builder /app/target/native/nexus-sample-app-1.0.0-SNAPSHOT /app/nexus-sample-app
COPY --from=builder /app/.env /app/.env
COPY --from=builder /app/migrations /app/migrations

# Set executable permission
RUN chmod +x /app/nexus-sample-app

EXPOSE 15000

ENTRYPOINT ["/app/nexus-sample-app"]
