import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nexus.Main;
import org.nexus.NexusExecutor;
import org.nexus.Response;
import org.nexus.handlers.testing.TestRouteRegistry;

class LoadTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Main server;
  private HttpClient http;
  private String baseUrl;

  private static boolean isPrime(final int num) {
    if (num <= 1) {
      return false;
    }
    if (num <= 3) {
      return true;
    }
    if (num % 2 == 0 || num % 3 == 0) {
      return false;
    }

    for (int i = 5; (long) i * i <= num; i += 6) {
      if (num % i == 0 || num % (i + 2) == 0) {
        return false;
      }
    }
    return true;
  }

  @BeforeEach
  void setUp() throws Exception {
    // note: the tests CAN reach the GeneratedRoutes table, these routes just have priority
    var testRoutes = new TestRouteRegistry()
        .get("/primes/:number", rc -> {
          int number = Integer.parseInt(rc.pathParams().get("number"));
          return primes(number);
        });

    server = new Main();
    server.start(0, testRoutes); // <-- inject the test route handler
    baseUrl = "http://127.0.0.1:" + server.getPort();
    http = HttpClient.newHttpClient();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void single_lightLoad_returns200() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/primes/100000")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    System.out.println(res.body());
    assertTrue(res.body().contains("Found 9592 primes"));
  }

  @Test
  void single_mediumLoad_returns200() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/primes/100000000")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    System.out.println(res.body());
    assertTrue(res.body().contains("Found 5761455 primes"));
  }

  @Test
  void single_heavyLoad_returns200() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/primes/200000000")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    System.out.println(res.body());
    assertTrue(res.body().contains("Found 11078937 primes"));
  }

  @Test
  void concurrent_lightLoad_returns200() throws Exception {
    int numberOfConnections = 500;
    int numberToTest = 100000;

    // Create a list of CompletableFuture for each request
    List<CompletableFuture<HttpResponse<String>>> futures = IntStream.range(0, numberOfConnections)
        .mapToObj(i ->
            CompletableFuture.supplyAsync(() -> {
              try {
                return http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/primes/" + numberToTest))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString()
                );
              } catch (Exception e) {
                throw new RuntimeException("Request failed", e);
              }
            }, NexusExecutor.INSTANCE.get())
        )
        .toList();

    // Wait for all requests to complete
    CompletableFuture<Void> allOf = CompletableFuture.allOf(
        futures.toArray(new CompletableFuture[0])
    );

    // Get all responses
    List<HttpResponse<String>> responses = allOf.thenApply(v ->
        futures.stream()
            .map(CompletableFuture::join)
            .toList()
    ).get(); // This will block until all requests are complete

    // Verify all responses
    int successCount = 0;
    for (HttpResponse<String> response : responses) {
      if (response.statusCode() == 200) {
        successCount++;
        assertTrue(response.body().contains("Found 9592 primes"));
      }
    }

    System.out.printf(
        "Completed %d requests with %d successful responses%n",
        numberOfConnections, successCount);
    assertEquals(numberOfConnections, successCount,
        "All requests should complete successfully");
  }

  private CompletableFuture<Response<String>> primes(int number) {
    long startTime = System.currentTimeMillis();

    return calculatePrimesAsync(number, Runtime.getRuntime().availableProcessors())
        .thenApply(primes -> {
          long duration = System.currentTimeMillis() - startTime;
          return new Response<>(
              200,
              "Found %s primes in %dms".formatted(primes.size(), duration)
          );
        })
        .exceptionally(ex -> {
          return new Response<>(500, "Error calculating primes: " + ex.getMessage());
        });
  }

  private CompletableFuture<List<Integer>> calculatePrimesAsync(int n, int threadCount) {
    List<CompletableFuture<List<Integer>>> futures = new ArrayList<>();
    int chunkSize = n / threadCount;

    for (int i = 0; i < threadCount; i++) {
      final int start = i * chunkSize + 1;
      final int end = (i == threadCount - 1) ? n : (i + 1) * chunkSize;

      CompletableFuture<List<Integer>> future = CompletableFuture.supplyAsync(() -> {
        List<Integer> primes = new ArrayList<>();
        for (int num = start; num <= end; num++) {
          if (isPrime(num)) {
            primes.add(num);
          }
        }
        return primes;
      }, NexusExecutor.INSTANCE.get());

      futures.add(future);
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(v -> {
          List<Integer> allPrimes = new ArrayList<>();
          futures.forEach(f -> allPrimes.addAll(f.join()));
          return allPrimes;
        });
  }
}
