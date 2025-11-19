package org.nexus.controllers;

import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusExecutor;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.QueryParam;
import org.nexus.annotations.RequestBody;
import org.nexus.dto.TestUserDTO;
import org.nexus.enums.HttpMethod;

@Singleton
public class EndToEndTestsController {

  @Mapping(type = HttpMethod.GET, endpoint = "/health")
  public CompletableFuture<Response<Map<String, Object>>> healthCheck() {
    return CompletableFuture.supplyAsync(() -> {
      Map<String, Object> response = new HashMap<>();
      response.put("status", "UP");
      response.put("service", "test-service");
      return new Response<>(200, response);
    }, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.POST, endpoint = "/echo")
  public CompletableFuture<Response<String>> echo(@RequestBody String body) {
    return CompletableFuture.supplyAsync(
        () -> new Response<>(200, body),
        NexusExecutor.get()
    );
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/path/:id/:name")
  public CompletableFuture<Response<Map<String, Object>>> pathParams(
      String id, String name) {

    return CompletableFuture.supplyAsync(() -> {
      Map<String, Object> response = new HashMap<>();
      response.put("id", id);
      response.put("name", name);
      return new Response<>(200, response);
    }, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.POST, endpoint = "/users/register")
  public CompletableFuture<Response<TestUserDTO>> registerUser(
      @RequestBody Map<String, Object> userData) {
    return CompletableFuture.supplyAsync(() -> {
      // Simple validation
      if (!userData.containsKey("username") || !userData.containsKey("password")) {
        throw new IllegalArgumentException("Username and password are required");
      }

      // Create a response DTO
      TestUserDTO user = new TestUserDTO();
      user.setUsername(userData.get("username").toString());
      // In a real app, you'd hash the password before storing it
      user.setPassword("[PROTECTED]");

      return new Response<>(200, user);
    }, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/query/params")
  public CompletableFuture<Response<Map<String, Object>>> queryParams(
      @QueryParam(value = "name", defaultValue = "guest") String name,
      @QueryParam("age") Integer age) {

    return CompletableFuture.supplyAsync(() -> {
      Map<String, Object> response = new HashMap<>();
      response.put("name", name);
      if (age != null) {
        response.put("age", age);
      }
      return new Response<>(200, response);
    }, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/error/simulate")
  public CompletableFuture<Response<String>> simulateError() {
    return CompletableFuture.supplyAsync(
        () -> {
          throw new RuntimeException("This is a simulated error for testing purposes");
        },
        NexusExecutor.get()
    );
  }

  @Mapping(type = HttpMethod.POST, endpoint = "/validate/body")
  public CompletableFuture<Response<Map<String, Object>>> validateBody(
      @RequestBody Map<String, Object> body) {
    return CompletableFuture.supplyAsync(() -> {
      if (body == null || body.isEmpty()) {
        throw new IllegalArgumentException("Request body cannot be empty");
      }
      return new Response<>(200, body);
    }, NexusExecutor.get());
  }
}
