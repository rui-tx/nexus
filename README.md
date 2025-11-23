# nexus

Netty-based web server

# Requirements

- Java 25
- Maven
- GraalVM, for [native image](https://www.graalvm.org/latest/reference-manual/native-image/)

# Features

- [Netty-based](https://netty.io/), async by default
- GraalVM [native image](https://www.graalvm.org/latest/reference-manual/native-image/) compatible
- Route table generated at build time
- `reflection-config.json` automatically generated at build time for objects annotated with
  `@RequestBody` (GraalVM native image)
- Dependency injection with [avaje inject](https://avaje.io/inject/)
- Middleware system
- Database system with HikariCP and JDBC

# Sonar

```
mvn clean verify sonar:sonar -Dsonar.projectKey=nexus -Dsonar.projectName=nexus -Dsonar.token=sqp_...
```

```
Apple MacBook Air M3 16GB
bombardier version 2.0.2 darwin/arm64
bombardier -c 1024 -n 5000000 http://localhost:15000/api/v1/heartbeat
Bombarding http://localhost:15000/api/v1/heartbeat with 5000000 request(s) using 1024 connection(s)
 5000000 / 5000000 [============================================================================] 100.00% 109665/s 45s
Done!
Statistics        Avg      Stdev        Max
  Reqs/sec    110041.57   16194.75  170102.92
  Latency        9.31ms     5.04ms   297.72ms
  HTTP codes:
    1xx - 0, 2xx - 5000000, 3xx - 0, 4xx - 0, 5xx - 0
    others - 0
  Throughput:    30.90MB/s
```

# Configuration

## Server Configuration

### Basic Settings

- **BIND_ADDRESS**: IP address the server will bind to. `0.0.0.0` means it listens on all available
  interfaces.
- **SERVER_PORT**: Port number the server will run on.

> Podman/Docker: Don't change the `SERVER_PORT` here, but instead change it at the
`docker-compose.yml`. Just redirect to the correct port.

### SSL / HTTPS

- **SSL_ENABLED**: Enables HTTPS if set to `true`. Default is `false`.
- **SSL_KEYSTORE_PATH**: Path to the keystore file containing the SSL certificate.
- **SSL_KEYSTORE_PASSWORD**: Password to access the keystore.
- **SSL_KEY_PASSWORD**: Password for the key inside the keystore.

> 💡 To generate a self-signed certificate:
> ```
> keytool -genkeypair -alias nexus -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650
> ```

> ‼️ SSL is experimental, please use a reverse proxy

## Database Configuration

- **DB[]_NAME**: Logical name of the database. This is what you will call in nexus, for example,
  `nexus-db-postgresql`
- **DB[]_TYPE**: Type of database `SQLITE` or `POSTGRES`. If another type of DB is necessary, just
  create a new connector for it. The pool is HikariCP so it should be easy.
- **DB[]_URL**: JDBC connection string to the database.
- **DB[]_POOL_SIZE**: Number of connections in the pool.
- **DB[]_AUTO_COMMIT**: Whether auto-commit is enabled.
- **DB[]_CONNECTION_TIMEOUT**: Timeout for database connections (in milliseconds).
- **DB[]_MIGRATIONS_PATH**: Path to migration scripts.

You can add N database connectors to nexus. Just follow this example:

### Example: PostgreSQL (Podman)

```
DB1_NAME=nexus-db-postgresql
DB1_TYPE=POSTGRES
DB1_URL=jdbc:postgresql://database:5432/nexus-db
DB1_USER=username
DB1_PASSWORD=password
DB1_POOL_SIZE=20
DB1_AUTO_COMMIT=true
DB1_CONNECTION_TIMEOUT=30000
DB1_MIGRATIONS_PATH=/app/migrations/postgres
```

> There's a `.env.example` with a default config

## Logs

The logs configuration is at `nexus-core` resources folder, at `logback.xml`
