#!/bin/sh

# Extract the revision from pom.xml
REVISION=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' pom.xml | head -n 1)

# Check if revision was found
if [ -z "$REVISION" ]; then
  echo "Revision not found in pom.xml"
  exit 1
fi

# Generate Dockerfile with dynamic binary name
cat > Dockerfile <<EOF
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
RUN mvn compile -B

# copy the rest of the source code
COPY . .

RUN mvn package -Pnative -DskipTests

FROM debian:bookworm-slim

LABEL org.nexus.image.version="$REVISION"

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    vim \
    htop \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/nexus-core/target/native/nexus-$REVISION /app/nexus
COPY --from=builder /app/.env /app/.env
COPY --from=builder /app/migrations /app/migrations

RUN chmod +x /app/nexus

EXPOSE 15000

ENTRYPOINT ["/app/nexus"]
EOF

echo "Dockerfile generated with revision: $REVISION"

# Build and tag
podman build -t nexus:"$REVISION" .
podman tag nexus:"$REVISION" nexus:latest

echo "Image generated with revision: $REVISION"

# Optional: push to registry
# podman tag localhost/nexus:$REVISION docker.io/yourusername/nexus:$REVISION
# podman push docker.io/yourusername/nexus:$REVISION