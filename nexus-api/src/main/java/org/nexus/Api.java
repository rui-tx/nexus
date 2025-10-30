package org.nexus;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.Secured;
import org.nexus.enums.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Api {

  private static final Logger LOGGER = LoggerFactory.getLogger(Api.class);
  private static final String ENDPOINT = "/api/v1";

  @Secured(value = "USER", permissions = "R")
  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/heartbeat")
  public CompletableFuture<Response<String>> pong() {
    CompletableFuture<Response<String>> future = new CompletableFuture<>();
    future.complete(new Response<>(200, "up"));
    return future;
  }

  @Secured(permitAll = true)
  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/sleep/:duration")
  public CompletableFuture<Response<String>> sleep(int duration) {
    long startTime = System.currentTimeMillis();

    return CompletableFuture.supplyAsync(() -> {
      try {
        TimeUnit.MILLISECONDS.sleep(duration);
        long elapseTime = System.currentTimeMillis() - startTime;

        return new Response<>(
            200,
            "Response delayed by %dms. Total duration: %sms".
                formatted(duration, elapseTime)
        );
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return new Response<>(500, "Thread interrupted during sleep.");
      }
    }, NexusExecutor.INSTANCE.get());
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
          System.err.println("Error fetching todos: " + ex.getMessage());
          return new Response<>(503, "Failed to connect to external service.");
        });
  }
}