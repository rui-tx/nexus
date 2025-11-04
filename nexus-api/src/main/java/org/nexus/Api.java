package org.nexus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.RequestBody;
import org.nexus.annotations.Secured;
import org.nexus.dto.UserDto;
import org.nexus.enums.HttpMethod;
import org.nexus.enums.ProblemDetailsTypes;
import org.nexus.exceptions.ProblemDetailsException;
import org.nexus.interfaces.ProblemDetails.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Api {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger LOGGER = LoggerFactory.getLogger(Api.class);
  private static final String ENDPOINT = "/api/v1";

  @Secured(permitAll = true)
  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/heartbeat")
  public CompletableFuture<Response<String>> pong() {
    return CompletableFuture.supplyAsync(
        () -> new Response<>(200, "up"),
        NexusExecutor.INSTANCE.get());
  }

  @Secured
  @Mapping(type = HttpMethod.POST, endpoint = ENDPOINT + "/post/:id")
  public CompletableFuture<Response<String>> testPOST(int id, @RequestBody PostRequest request) {
    return CompletableFuture.supplyAsync(
        () -> new Response<>(200, "%d: %s %s".formatted(id, request.foo(), request.bar())),
        NexusExecutor.INSTANCE.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/external-call")
  public CompletableFuture<Response<List<Todo>>> externalCall() {
    String apiUrl = "https://jsonplaceholder.typicode.com/todos";

    HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
        .GET()
        .header("Accept", "application/json")
        .build();

    return NexusHttpClient.INSTANCE.get().sendAsync(
            request,
            HttpResponse.BodyHandlers.ofInputStream()
        )
        .thenApply(httpResponse -> {
          int status = httpResponse.statusCode();
          try (InputStream bodyStream = httpResponse.body()) {
            List<Todo> todos = MAPPER.readValue(bodyStream, new TypeReference<>() {
            });
            return new Response<>(status, todos);
          } catch (IOException e) {
            LOGGER.error("Error parsing JSON: {}", e.getMessage());
            return new Response<>(500, Todo.emptyList());
          }
        })
        .exceptionally(ex -> {
          LOGGER.error("Error fetching todos: {}", ex.getMessage());
          return new Response<>(503, Todo.emptyList()) {
          };
        });
  }

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/user")
  public CompletableFuture<Response<UserDto>> getUser() {
    String apiUrl = "https://jsonplaceholder.typicode.com/users/1";

    HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
        .GET()
        .header("Accept", "application/json")
        .build();

    return NexusHttpClient.INSTANCE.get().sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString()
        )
        .thenApply(httpResponse -> {
          int status = httpResponse.statusCode();
          try {
            UserDto user = MAPPER.readValue(httpResponse.body(), UserDto.class);
            return new Response<>(status, user);
          } catch (Exception e) {
            LOGGER.error("Error parsing user: {}", e.getMessage());
            throw new ProblemDetailsException(new Single(
                ProblemDetailsTypes.SERVER_ERROR,
                "Internal Server Error",
                500,
                "An unexpected error occurred while trying to fetch user",
                "unknown",
                Map.of("exception", Objects.toString(e.getMessage(), e.getClass().getSimpleName()))
            ));
          }
        })
        .exceptionally(ex -> {
          LOGGER.error("Error fetching user: {}", ex.getMessage());
          throw new ProblemDetailsException(new Single(
              ProblemDetailsTypes.SERVER_ERROR,
              "Internal Server Error",
              500,
              "An unexpected error occurred while trying to fetch user",
              "unknown",
              Map.of("exception", Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()))
          ));
        });
  }


  public record PostRequest(String foo, String bar) {

  }

  public record Todo(int userId, int id, String title, boolean completed) {

    public static List<Todo> emptyList() {
      return new ArrayList<>();
    }

  }
}