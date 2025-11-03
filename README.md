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

# Usage

Check if everything is working

```
mvn test
```

## Jar

Generate the `.jar`

```
mvn clean package -Puber-jar
```

Run it

```
java -jar nexus-core/target/nexus-core-[version]-jar-with-dependencies.jar
```

## GraalVM native image

Generate bin file

```
mvn clean package -Pnative
```

Run it

```
./nexus-core/target/native/nexus-[version]
```

## Docker

Build and run

```
podman compose up --build
```

## Test it

Server port is `15000`

```
curl -s 'http://localhost:15000/api/v1/heartbeat'
{
  "date": "2025-10-28T13:14:57.258053Z[Europe/Lisbon]",
  "status": 200,
  "data": "up"
}
```

## Example

Api.java

```java
public class Api {

  private static final Logger LOGGER = LoggerFactory.getLogger(Api.class);
  private static final String ENDPOINT = "/api/v1";

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/heartbeat")
  public CompletableFuture<Response<String>> pong() {
    return CompletableFuture.supplyAsync(
        () -> new Response<>(200, "up"),
        NexusExecutor.INSTANCE.get());
  }

  @Mapping(type = HttpMethod.POST, endpoint = ENDPOINT + "/post/:id")
  public CompletableFuture<Response<String>> testPOST(int id, @RequestBody PostRequest request) {
    return CompletableFuture.supplyAsync(
        () -> new Response<>(200, "%d: %s %s".formatted(id, request.foo(), request.bar())),
        NexusExecutor.INSTANCE.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/external-call")
  public CompletableFuture<Response<String>> externalCall() {
    String apiUrl = "https://jsonplaceholder.typicode.com/todos";

    HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
        .GET()
        .header("Accept", "application/json")
        .build();

    return NexusHttpClient.INSTANCE.get().sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString()
        )
        .thenApply(httpResponse ->
            new Response<>(
                httpResponse.statusCode(),
                httpResponse.body()
            ))
        .exceptionally(ex -> {
          LOGGER.error("Error fetching todos: {}", ex.getMessage());
          return new Response<>(503, "Failed to connect to external service.");
        });
  }

  public record PostRequest(String foo, String bar) {

  }
}
```
