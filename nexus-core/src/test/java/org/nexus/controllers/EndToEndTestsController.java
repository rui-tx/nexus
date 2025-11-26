package org.nexus.controllers;

import io.netty.handler.codec.http.FullHttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.nexus.CachedHttpResponse;
import org.nexus.NexusDatabase;
import org.nexus.NexusExecutor;
import org.nexus.NexusStaticResponseRegistry;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.QueryParam;
import org.nexus.annotations.RequestBody;
import org.nexus.dto.TestUserDTO;
import org.nexus.enums.HttpMethod;

@Singleton
public class EndToEndTestsController {

  static {
    NexusStaticResponseRegistry.register("cache", "OK", 200);
  }

  private final NexusDatabase db1;

  @Inject
  public EndToEndTestsController(NexusDatabase db1) {
    this.db1 = db1;
  }

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

  @Mapping(type = HttpMethod.GET, endpoint = "/cache")
  public CompletableFuture<Response<String>> echo() {
    FullHttpResponse preComputed = NexusStaticResponseRegistry.get("cache");
    return CompletableFuture.supplyAsync(() -> new CachedHttpResponse<>(preComputed),
        NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/db-integration")
  public CompletableFuture<Response<List<User>>> dbIntegration() {
    return CompletableFuture.supplyAsync(() -> {

      db1.update("""
          CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT UNIQUE NOT NULL,
            age INTEGER,
            active INTEGER DEFAULT 1
          )
          """);

      db1.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Alice", "alice@example.com", 25);
      db1.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Bob", "bob@example.com", 30);
      db1.update("INSERT INTO users (name, email, age) VALUES (?, ?, ?)",
          "Charlie", "charlie@example.com", 35);

      List<User> users = db1.query(
          "SELECT id, name, email, age FROM users ORDER BY age",
          rs -> new User(
              rs.getInt("id"),
              rs.getString("name"),
              rs.getString("email"),
              rs.getInt("age")
          )
      );

      return new Response<>(200, users);
    }, NexusExecutor.get());
  }

  public record User(int id, String name, String email, int age) {

  }
}
