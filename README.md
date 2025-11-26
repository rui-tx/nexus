# nexus

Netty-based web server

# Requirements

- Java 25
- Maven
- GraalVM, for [native image](https://www.graalvm.org/latest/reference-manual/native-image/)
- Podman/Docker (If installing to a local maven repository)

# Features

- [Netty-based](https://netty.io/), async by default
- GraalVM [native image](https://www.graalvm.org/latest/reference-manual/native-image/) compatible
- Route table generated at build time
- `reflection-config.json` automatically generated at build time for objects annotated with
  `@RequestBody` (GraalVM native image)
- Dependency injection with [avaje inject](https://avaje.io/inject/)
- Database with HikariCP and JDBC, Basic Middleware and JWT system

# Get started

## Install

To use this `nexus` there are 2 ways, GitHub and a local maven repository

### GitHub repository

To use the GitHub maven repository, there is a need for an API key, even just for reads. However
there is a [workaround](https://github.com/jcansdale-test/maven-consume).

The `url` is encoded with an API key from this repo that is read-only and should *just work™*. If
not, try the local repo method instead

```
<repositories>
  <repository>
    <id>github-nexus</id>
    <!-- Encoded API read token from the project -->
    <url>https://public:&#103;hp_kP4OdVpZnAWCBRPyg8PTjjGHTKo4zR1dWCsg@maven.pkg.github.com/rui-tx/*</url>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.nexus</groupId>
      <artifactId>nexus-bom</artifactId>
      <version>0.1-ALPHA-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.nexus</groupId>
    <artifactId>nexus-starter</artifactId>
  </dependency>
</dependencies>
```

### Local repository

To use the local maven repository, clone this repo and at the root folder and run `mvn install`

After that in the project you want to use the `nexus`, add this to `pom.xml`

```
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.nexus</groupId>
      <artifactId>nexus-bom</artifactId>
      <!-- change the version here -->
      <version>0.1-ALPHA-SNAPSHOT</version> 
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.nexus</groupId>
    <artifactId>nexus-starter</artifactId>
  </dependency>
</dependencies>

<repositories>
    <repository>
        <id>local-nexus</id>
        <name>Local Nexus Repository</name>
        <url>file://${project.basedir}/lib/maven-repo</url>
    </repository>
</repositories>
```

> The project uses [Testcontainers](https://testcontainers.com/) to test the Postgresql logic, so
> Podman/Docker is needed to
> pass
> the tests.
> If you don't want to install it, you can ignore them with `mvn install -DskipTests`

## Usage

In this repo, there is a `sample-app` with everything to get start with, including some endpoint
with DB integration.

When building controllers there is 1 way of creating them -> Return type must be CompletableFuture<
Response<T>>
Why? Because right now the processor is only in the early stages. However I do like it, it means
that it is very focused and simple and integrates well with the async nature of `netty`

Example Controller <-> Service <-> Repository

```java

@Singleton
public class Controller {

  private final Service service;

  @Inject
  public Controller(Service service) {
    this.service = service;
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/echo")
  public CompletableFuture<Response<String>> echo() {
    return CompletableFuture.supplyAsync(service::echo, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/entry/:name")
  public CompletableFuture<Response<String>> getSample(String name) {
    return service.getSample(name);
  }

  @Mapping(type = HttpMethod.POST, endpoint = "/entry/:name")
  public CompletableFuture<Response<String>> postSample(String name) {
    return service.postSample(name);
  }
}
```

```java

@Singleton
public class Service {

  static {
    NexusStaticResponseRegistry.register("echo", "OK", 200);
  }

  private final Repository repository;

  @Inject
  public Service(Repository repository) {
    this.repository = repository;
  }

  public Response<String> echo() {
    FullHttpResponse preComputed = NexusStaticResponseRegistry.get("echo");
    return new CachedHttpResponse(preComputed);
  }

  public CompletableFuture<Response<String>> getSample(String name) {
    return CompletableFuture.supplyAsync(
        () -> repository.getSample(name),
        NexusExecutor.get()
    ).thenApply(count -> {
      String body = String.valueOf(count);
      return new Response<>(200, body);
    });
  }

  public CompletableFuture<Response<String>> postSample(String name) {
    return CompletableFuture.supplyAsync(
        () -> repository.postSample(name),
        NexusExecutor.get()
    ).thenApply(inserted -> {
      String body = String.valueOf(inserted);
      return new Response<>(201, body);
    });
  }
}
```

```java

@Singleton
public class Repository {

  private static final Logger LOGGER = LoggerFactory.getLogger(Repository.class);

  private final NexusDatabase db1;

  @Inject
  public Repository(@Named(DEFAULT_DB) NexusDatabase db1) {
    this.db1 = db1;
  }

  public Integer getSample(String name) {
    try {
      return db1.query(
          "SELECT COUNT(*) FROM test t WHERE t.name = ?",
          rs -> rs.getInt(1),
          name
      ).getFirst();
    } catch (DatabaseException e) {
      LOGGER.error(e.getMessage(), e);
      return null;
    }
  }

  public Boolean postSample(String name) {
    return db1.insert(
        "INSERT INTO test (name) VALUES (?)",
        _ -> true,
        name
    );
  }
}
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

> There's a `.env.example` with a default config at `sample-app`

## Logs

The logs configuration is at `nexus-core` resources folder, at `logback.xml`

# Performance

Simple endpoint with no business logic, just a `200 OK` type response

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

# Code Quality

If you want to check out the code quality and issues, there is integration
with [Jacoco](https://www.eclemma.org/jacoco/)
and [SonarQube](https://www.sonarsource.com/products/sonarqube/)

To use it, install SonarQube CE, create a project and an API key and pass it here

```
mvn clean verify sonar:sonar \         
  -Dsonar.projectKey=nexus \
  -Dsonar.projectName='nexus' \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=sqp```

```

# Goals

## What this project is

- A fun learning exercise, to become a better programmer, nothing more and nothing less.

## What this project is not

- A replacement for anything. If you need and API server, please use Spring
  Boot/Micronaut/Quarkus/anything else. Like I said above, I'm just learning and this probably will
  never be a finish product to use in a production environment.