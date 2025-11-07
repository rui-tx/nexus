FROM vegardit/graalvm-maven:latest-java25 as builder

WORKDIR /app

# Copy parent pom
COPY pom.xml .

# Copy all module pom.xml files
COPY nexus-bom/pom.xml nexus-bom/
COPY nexus-commons/pom.xml nexus-commons/
COPY nexus-annotations/pom.xml nexus-annotations/
COPY nexus-api/pom.xml nexus-api/
COPY nexus-core/pom.xml nexus-core/

# download dependencies, this layer will be cached
RUN mvn dependency:go-offline -B

# copy the rest of the source code
COPY . .

RUN mvn package -Pnative -DskipTests

FROM debian:bookworm-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/nexus-core/target/native/nexus-0.1-ALPHA-SNAPSHOT /app/nexus
COPY --from=builder /app/.env /app/.env

RUN chmod +x /app/nexus

EXPOSE 15000

ENTRYPOINT ["/app/nexus"]
