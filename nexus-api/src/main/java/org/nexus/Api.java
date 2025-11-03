package org.nexus;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.RequestBody;
import org.nexus.enums.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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