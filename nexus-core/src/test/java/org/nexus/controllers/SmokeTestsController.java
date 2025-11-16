package org.nexus.controllers;

import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.QueryParam;
import org.nexus.annotations.RequestBody;
import org.nexus.dto.PathParamResponseTestDTO;
import org.nexus.enums.HttpMethod;
import org.nexus.enums.ResponseType;

@Singleton
public class SmokeTestsController {

  @Mapping(type = HttpMethod.GET, endpoint = "/found")
  public CompletableFuture<Response<String>> found_returns200() {
    return CompletableFuture.completedFuture(new Response<>(200, "found"));
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/throw")
  public CompletableFuture<Response<String>> unexpectedException_returns500() {
    throw new RuntimeException("boom");
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/path/:path1/:path2")
  public CompletableFuture<Response<PathParamResponseTestDTO>> sentCorrectPathParams_returns200(
      int pathParam1, String pathParam2) {
    return CompletableFuture.completedFuture(
        new Response<>(200, new PathParamResponseTestDTO(pathParam1, pathParam2)));
  }

  // Query parameter test endpoints
  @Mapping(type = HttpMethod.GET, endpoint = "/query/single")
  public CompletableFuture<Response<Map<String, String>>> queryParamTest(
      @QueryParam("name") String name) {
    return CompletableFuture.completedFuture(
        new Response<>(200, Map.of("name", name)));
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/query/multiple")
  public CompletableFuture<Response<Map<String, Object>>> multipleQueryParamsTest(
      @QueryParam("name") String name,
      @QueryParam("age") int age,
      @QueryParam("active") String active) {
    return CompletableFuture.completedFuture(
        new Response<>(200, Map.of(
            "name", name,
            "age", age,
            "active", active
        )));
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/query/optional")
  public CompletableFuture<Response<Map<String, Object>>> optionalQueryParamTest(
      @QueryParam(value = "required", required = true) String required,
      @QueryParam(value = "optional", required = false, defaultValue = "default") String optional) {
    return CompletableFuture.completedFuture(
        new Response<>(200, Map.of(
            "required", required,
            "optional", optional
        )));
  }

  // Request body test endpoints
  @Mapping(type = HttpMethod.POST, endpoint = "/body/json")
  public CompletableFuture<Response<Map<String, Object>>> jsonBodyTest(
      @RequestBody Map<String, Object> body) {
    return CompletableFuture.completedFuture(
        new Response<>(200, body));
  }

  @Mapping(type = HttpMethod.POST, endpoint = "/body/echo", responseType = ResponseType.TEXT)
  public CompletableFuture<Response<String>> echoBodyTest(
      @RequestBody String rawBody) {
    return CompletableFuture.completedFuture(
        new Response<>(200, rawBody));
  }

  @Mapping(type = HttpMethod.POST, endpoint = "/body/validate")
  public CompletableFuture<Response<Map<String, Object>>> validateBodyTest(
      @RequestBody Map<String, Object> body) {
    if (body == null || body.isEmpty()) {
      return CompletableFuture.completedFuture(
          new Response<>(400, Map.of("error", "Request body cannot be empty")));
    }
    if (!body.containsKey("username") || ((String) body.getOrDefault("username", "")).isBlank()) {
      return CompletableFuture.completedFuture(
          new Response<>(400, Map.of("error", "Username is required")));
    }
    return CompletableFuture.completedFuture(
        new Response<>(200, body));
  }
}
