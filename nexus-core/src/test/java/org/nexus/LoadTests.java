package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoadTests {

  private static TestNexusApplication app;
  private static HttpClient http;
  private static String baseUrl;

  @BeforeAll
  static void setUp() {
    System.setProperty("nexus.test", "true");
    app = TestNexusApplication.getInstance();
    app.start(new String[]{});
    http = NexusHttpClient.get();
    baseUrl = app.getBaseUrl();
  }

  @AfterAll
  static void tearDown() {
    if (app != null) {
      app.stop();
    }
  }

  @Test
  @Order(1)
  void single_lightLoad_returns200() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/primes/100000")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    System.out.println(res.body());
    assertTrue(res.body().contains("Found 9592 primes"));
  }

  @Test
  @Order(2)
  void single_mediumLoad_returns200() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/primes/1000000")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    System.out.println(res.body());
    assertTrue(res.body().contains("Found 78498 primes"));
  }

  @Test
  @Order(3)
  void single_heavyLoad_returns200() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/primes/5000000")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    System.out.println(res.body());
    assertTrue(res.body().contains("Found 348513 primes"));
  }

  @Test
  @Order(4)
  void concurrent_lightLoad_returns200() throws Exception {
    int numberOfConnections = 50;
    int numberToTest = 1_000_000;
    String expectedResponse = "Found 78498 primes";

    testConcurrentRequests(
        numberOfConnections,
        baseUrl + "/primes/" + numberToTest,
        response -> {
          assertEquals(200, response.statusCode());
          assertTrue(response.body().contains(expectedResponse));
        }
    );
  }

  @Test
  @Order(5)
  void concurrent_mediumLoad_returns200() throws Exception {
    int numberOfConnections = 100;
    int numberToTest = 1_000_000;
    String expectedResponse = "Found 78498 primes";

    testConcurrentRequests(
        numberOfConnections,
        baseUrl + "/primes/" + numberToTest,
        response -> {
          assertEquals(200, response.statusCode());
          assertTrue(response.body().contains(expectedResponse));
        }
    );
  }

  @Test
  @Order(6)
  void concurrent_heavyLoad_returns200() throws Exception {
    int numberOfConnections = 250;
    int numberToTest = 1_000_000;
    String expectedResponse = "Found 78498 primes";

    testConcurrentRequests(
        numberOfConnections,
        baseUrl + "/primes/" + numberToTest,
        response -> {
          assertEquals(200, response.statusCode());
          assertTrue(response.body().contains(expectedResponse));
        }
    );
  }

  private void testConcurrentRequests(
      int numberOfConnections,
      String url,
      Consumer<HttpResponse<String>> responseValidator) throws Exception {
    // Create a list of CompletableFuture for each request
    List<CompletableFuture<HttpResponse<String>>> futures = IntStream.range(0, numberOfConnections)
        .mapToObj(i ->
            CompletableFuture.supplyAsync(() -> {
              try {
                return http.send(
                    HttpRequest.newBuilder(URI.create(url))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                );
              } catch (Exception e) {
                throw new RuntimeException("Request failed for URL: " + url, e);
              }
            }, NexusExecutor.get())
        )
        .toList();

    // Wait for all requests to complete and get responses
    List<HttpResponse<String>> responses = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        )
        .thenApply(v ->
            futures.stream()
                .map(CompletableFuture::join)
                .toList()
        )
        .get();

    // Verify all responses
    responses.forEach(responseValidator);
    assertEquals(numberOfConnections, responses.size(),
        "Number of responses should match number of connections");
  }
}
