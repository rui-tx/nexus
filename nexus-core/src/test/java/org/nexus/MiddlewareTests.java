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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Nexus Core Middleware Tests")
class MiddlewareTests {

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

  @Test
  @Order(1)
  @DisplayName("Should return 200 for a existing endpoint and with HTTPS headers")
  void found_returns200() throws Exception {
    HttpResponse<String> res = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/found")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    assertEquals(
        "nosniff",
        res.headers().firstValue("X-Content-Type-Options").get());
    assertEquals(
        "DENY",
        res.headers().firstValue("X-Frame-Options").get());
    assertEquals(
        "1; mode=block",
        res.headers().firstValue("X-XSS-Protection").get());
    assertEquals(
        "strict-origin-when-cross-origin",
        res.headers().firstValue("Referrer-Policy").get());
    assertEquals(
        "max-age=31536000; includeSubDomains",
        res.headers().firstValue("Strict-Transport-Security").get());
    assertTrue(res.body().contains("found"));
  }
}
