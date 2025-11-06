FROM vegardit/graalvm-maven:latest-java25 as builder

WORKDIR /app

COPY . .

RUN mvn package -Pnative -DskipTests

FROM debian:bookworm-slim

ENV PORT=15000

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates     && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/nexus-core/target/native/nexus-0.1-ALPHA-SNAPSHOT /app/nexus

RUN chmod +x /app/nexus

EXPOSE 15000

ENTRYPOINT /app/nexus -p 15000
