#!/bin/sh

# Extract the main project version from pom.xml
VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n 1)

if [ -z "$VERSION" ]; then
  echo "Version not found in pom.xml"
  exit 1
fi

BINARY_NAME="nexus-sample-app-$VERSION"
ARTIFACT_ID="nexus-sample-app"
M2_REPO_BASE="$HOME/.m2/repository"
TEMP_CACHE_DIR=".m2_local_cache"

echo "Preparing local Maven artifacts for Docker build..."
mkdir -p $TEMP_CACHE_DIR

cp -R "$M2_REPO_BASE"/org $TEMP_CACHE_DIR/
cp -R "$M2_REPO_BASE"/io $TEMP_CACHE_DIR/


# Generate Dockerfile
cat > Dockerfile <<EOF
FROM vegardit/graalvm-maven:latest-java25 as builder

WORKDIR /app

# Copy pom.xml
COPY pom.xml .

# .m2 copy
COPY $TEMP_CACHE_DIR/org /root/.m2/repository/org
COPY $TEMP_CACHE_DIR/io /root/.m2/repository/io

# download dependencies, this layer will be cached
RUN mvn compile -B

# copy the rest of the source code
COPY . .

# Run native compilation
RUN mvn package -Pnative -DskipTests

FROM debian:bookworm-slim

LABEL io.github.ruitx.nexus-sample-app.version="$VERSION"

WORKDIR /app

# Install runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Copy the built native executable from the builder stage
COPY --from=builder /app/target/native/$BINARY_NAME /app/$ARTIFACT_ID
COPY --from=builder /app/.env /app/.env
COPY --from=builder /app/migrations /app/migrations

# Set executable permission
RUN chmod +x /app/$ARTIFACT_ID

EXPOSE 15000

ENTRYPOINT ["/app/$ARTIFACT_ID"]
EOF

echo "Dockerfile generated with version: $VERSION"

# Build and tag
podman build --no-cache -t nexus-sample-app:"$VERSION" .
podman tag nexus-sample-app:"$VERSION" nexus-sample-app:latest

echo "Image generated with version: $VERSION"

# Clean up the temporary cache directory after the build is done
rm -rf $TEMP_CACHE_DIR

# Optional: push to registry
# podman tag localhost/nexus-sample-app:$REVISION docker.io/yourusername/nexus-sample-app:$REVISION
# podman push docker.io/yourusername/nexus-sample-app:$REVISION