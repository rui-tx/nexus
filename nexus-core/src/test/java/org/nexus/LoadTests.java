package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LoadTests {

  private static TestNexusApplication app;
  private static HttpClient httpClient;
  private static String baseUrl;

  @BeforeAll
  static void setUp() throws IOException {
    NexusConfig.closeInstance();

    // Create temp directory for the test database
    Path tempDir = Files.createTempDirectory("nexus-e2e-test-");
    Path dbFile = tempDir.resolve("test.db");
    Path migrationsDir = tempDir.resolve("migrations");

    Files.createDirectories(migrationsDir);

    // Create a test.env file
    Path envFile = tempDir.resolve(".env");
    String envContent = String.format("""
        DB1_NAME=test-db
        DB1_TYPE=SQLITE
        DB1_URL=jdbc:sqlite:%s
        DB1_POOL_SIZE=5
        DB1_AUTO_COMMIT=true
        DB1_CONNECTION_TIMEOUT=10000
        DB1_MIGRATIONS_PATH=%s
        
        # Server config
        BIND_ADDRESS=0.0.0.0
        SERVER_PORT=0
        """, dbFile, migrationsDir.toAbsolutePath());

    Files.writeString(envFile, envContent);

    NexusConfig config = NexusConfig.getInstance();
    config.setEnvFilePath(envFile.toString());
    config.init(new String[]{});

    System.setProperty("nexus.test", "true");
    app = TestNexusApplication.getInstance();
    app.start(new String[]{});

    httpClient = NexusHttpClient.get();
    baseUrl = app.getBaseUrl();
  }

  @AfterAll
  static void tearDown() {
    if (app != null) {
      app.stop();
    }
    NexusConfig.closeInstance();
  }

  private static String singleLoad(String url) throws Exception {
    HttpResponse<String> res = httpClient.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    return res.body();
  }

  @ParameterizedTest(name = "{0} load - limit {1}")
  @Order(1)
  @CsvSource({
      "light, 100000, 9592",
      "medium, 1000000, 78498",
      "heavy, 5000000, 348513"
  })
  void single_load_returns200(String loadType, int limit, int expectedPrimes) throws Exception {
    String response = singleLoad(baseUrl + "/primes/" + limit);
    System.out.println(response);
    assertTrue(response.contains("Found " + expectedPrimes + " primes"));
  }

  @ParameterizedTest(name = "{0} load - {1} concurrent connections")
  @Order(2)
  @CsvSource({
      "light, 50, 1000000, 78498",
      "medium, 100, 1000000, 78498",
      "heavy, 250, 1000000, 78498"
  })
  void concurrent_load_returns200(String loadType, int numberOfConnections, int numberToTest,
      int expectedPrimes) throws Exception {
    String expectedResponse = "Found " + expectedPrimes + " primes";

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
        .mapToObj(_ ->
            CompletableFuture.supplyAsync(() -> {
              try {
                return httpClient.send(
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
        .thenApply(_ ->
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
