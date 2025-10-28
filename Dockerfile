FROM vegardit/graalvm-maven:latest-java25 as builder

WORKDIR /app

COPY . .

RUN mvn package -Pnative

FROM debian:bookworm-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/nexus-core/target/nexus-aarch64 /app/nexus

RUN chmod +x /app/nexus

EXPOSE 15000

ENTRYPOINT ["/app/nexus", "-Xmx512m", "-Xms128m"]