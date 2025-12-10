package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueueTests {

  private static final ObjectMapper MAPPER = NexusUtils.DF_MAPPER;
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
    Path queueDataDir = tempDir.resolve("queue-data");

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
        
        QUEUE_DATA_DIR=%s
        
        # Server config
        BIND_ADDRESS=0.0.0.0
        SERVER_PORT=0
        """, dbFile, migrationsDir.toAbsolutePath(), queueDataDir.toAbsolutePath());

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

  private static void sendTestRequests(int count) throws Exception {
    for (int i = 0; i < count; i++) {
      httpClient.send(
          HttpRequest.newBuilder(URI.create(baseUrl + "/test"))
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString()
      );
    }
  }

  private static int getLogCount() throws Exception {
    String body = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/result"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString()
    ).body();

    return MAPPER.readTree(body)
        .path("data")
        .get(0)
        .asInt();
  }

  private static int waitForLogCountAtLeast(int target, long timeoutMs) throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    int last = -1;
    while (System.currentTimeMillis() < deadline) {
      last = getLogCount();
      if (last >= target) {
        return last;
      }
      Thread.sleep(200L);
    }
    return last;
  }

  @Test
  @Order(1)
  void testSimpleQueue() throws Exception {
    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/test"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );

    Thread.sleep(5000);

    assertEquals(202, response.statusCode());
    assertTrue(response.body().contains("status"), "Response should contain status field");
  }

  @Test
  @Order(3)
  void testMultipleMessages() throws Exception {

    int numMessages = 2000;

    for (int i = 0; i < numMessages; i++) {
      httpClient.send(
          HttpRequest.newBuilder(URI.create(baseUrl + "/test"))
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString()
      );
    }

    // Wait for processing
    Thread.sleep(10000);

    String result = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/result"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString()
    ).body();

    System.out.println(result);
    //assertEquals(numMessages + "", result);
  }

  @Test
  @Order(2)
  @Disabled
  void testPersistentQueueCrashRecovery_endToEnd() throws Exception {

    int initialCount = getLogCount();

    int phase1Messages = 20;
    sendTestRequests(phase1Messages);

    int afterPhase1 = waitForLogCountAtLeast(initialCount + phase1Messages, 15_000L);
    assertEquals(initialCount + phase1Messages, afterPhase1,
        "All phase 1 messages should be processed before restart");

    // Give the consumer time to auto-commit offsets to disk
    Thread.sleep(6_000L);

    // Simulate broker crash and application restart
    app.stop();
    NexusBeanScope.close();
    app.start(new String[]{});
    baseUrl = app.getBaseUrl();

    int phase2Messages = 20;
    sendTestRequests(phase2Messages);

    int afterPhase2 = waitForLogCountAtLeast(afterPhase1 + phase2Messages, 15_000L);

    assertEquals(initialCount + phase1Messages + phase2Messages, afterPhase2,
        "Messages after restart should be processed once without duplicates");
  }
}
