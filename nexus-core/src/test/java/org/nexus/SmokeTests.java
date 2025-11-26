package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.nexus.dto.ApiResponseDTO;
import org.nexus.dto.PathParamResponseTestDTO;
import org.nexus.dto.test.QueryResponseDTO;
import org.nexus.dto.test.UserResponseDTO;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Nexus Core Smoke Tests")
class SmokeTests {

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
  @DisplayName("Should return 200 for a existing endpoint")
  void found_returns200() throws Exception {
    HttpResponse<String> res = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/found")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("found"));
  }

  @Test
  @Order(2)
  @DisplayName("Should return 404 for a non-existing endpoint")
  void notFound_returns404() throws Exception {
    HttpResponse<String> res = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/not-found")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(404, res.statusCode());
  }

  @Test
  @Order(3)
  @DisplayName("Should return 500 for an unexcepted error")
  void unexpectedException_returns500() throws Exception {
    HttpResponse<String> res = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/throw")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(500, res.statusCode());
  }

  @Test
  @Order(4)
  @DisplayName("Should return 200 when sending a single correct path parameter")
  void queryParam_singleParam_returns200() throws Exception {
    String name = "testUser";
    String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);

    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/query/single?name=" + encodedName))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());

    ApiResponseDTO<QueryResponseDTO> apiResponse = MAPPER.readValue(
        response.body(),
        MAPPER.getTypeFactory().constructParametricType(
            ApiResponseDTO.class,
            QueryResponseDTO.class
        )
    );

    assertNotNull(apiResponse);
    assertEquals(200, apiResponse.status);
    assertNotNull(apiResponse.data);
    assertEquals(name, apiResponse.data.getName());
  }

  @Test
  @Order(5)
  @DisplayName("Should return 200 when sending correct path parameters")
  void sentCorrectPathParams_returns200() throws Exception {
    int pathParam1 = 1;
    String pathParam2 = "john";

    HttpResponse<String> res = httpClient.send(
        HttpRequest.newBuilder(
                URI.create(baseUrl + "/path/%d/%s".formatted(pathParam1, pathParam2)))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());

    JavaType javaType = MAPPER
        .getTypeFactory()
        .constructParametricType(ApiResponseDTO.class, PathParamResponseTestDTO.class);
    ApiResponseDTO<PathParamResponseTestDTO> api = MAPPER.readValue(res.body(), javaType);

    assertEquals(200, api.status);
    assertNotNull(api.date);
    assertEquals(pathParam1, api.data.pathParam1());
    assertEquals(pathParam2, api.data.pathParam2());
  }

  @Test
  @Order(6)
  @DisplayName("Should return 200 when sending multiple query parameters")
  void queryParam_multipleParams_returns200() throws Exception {
    String name = "testUser";
    int age = 30;
    boolean active = true;

    String url = String.format("%s/query/multiple?name=%s&age=%d&active=%b",
        baseUrl,
        URLEncoder.encode(name, StandardCharsets.UTF_8),
        age,
        active);

    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(url))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());

    ApiResponseDTO<QueryResponseDTO> apiResponse = MAPPER.readValue(
        response.body(),
        MAPPER.getTypeFactory().constructParametricType(
            ApiResponseDTO.class,
            QueryResponseDTO.class
        )
    );

    assertNotNull(apiResponse);
    assertEquals(200, apiResponse.status);
    assertNotNull(apiResponse.data);
    assertEquals(name, apiResponse.data.getName());
    assertEquals(age, apiResponse.data.getAge());
    assertEquals(active, apiResponse.data.isActive());
  }

  @Test
  @Order(7)
  @DisplayName("Should return 200 when sending optional query parameters")
  void queryParam_optionalParams_returns200() throws Exception {
    String required = "requiredValue";

    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(
                URI.create(baseUrl + "/query/optional?required=" + URLEncoder.encode(required,
                    StandardCharsets.UTF_8)))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());

    ApiResponseDTO<QueryResponseDTO> apiResponse = MAPPER.readValue(
        response.body(),
        MAPPER.getTypeFactory().constructParametricType(
            ApiResponseDTO.class,
            QueryResponseDTO.class
        )
    );

    assertNotNull(apiResponse);
    assertEquals(200, apiResponse.status);
    assertNotNull(apiResponse.data);
    assertEquals(required, apiResponse.data.getRequired());
    assertEquals("default", apiResponse.data.getOptional());
  }

  @Test
  @Order(8)
  @DisplayName("Should return 200 when sending valid JSON body")
  void jsonBody_validJson_returns200() throws Exception {
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("name", "testUser");
    requestBody.put("age", 30);
    requestBody.put("active", true);

    String requestBodyStr = MAPPER.writeValueAsString(requestBody);

    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/body/json"))
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr))
            .header("Content-Type", "application/json")
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());

    ApiResponseDTO<Map<String, Object>> apiResponse = MAPPER.readValue(
        response.body(),
        MAPPER.getTypeFactory().constructParametricType(
            ApiResponseDTO.class,
            Map.class
        )
    );

    assertNotNull(apiResponse);
    assertEquals(200, apiResponse.status);
    assertNotNull(apiResponse.data);
    assertEquals(requestBody.get("name"), apiResponse.data.get("name"));
    assertEquals(requestBody.get("age"), apiResponse.data.get("age"));
    assertEquals(requestBody.get("active"), apiResponse.data.get("active"));
  }

  @Test
  @Order(9)
  @DisplayName("Should return 200 when sending raw text body")
  void echoBody_rawText_returns200() throws Exception {
    String requestText = "This is a raw text request body";
    String requestBody = "\"" + requestText + "\"";

    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/body/echo"))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .header("Content-Type", "application/json")
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());

    ApiResponseDTO<String> apiResponse = MAPPER.readValue(
        response.body(),
        MAPPER.getTypeFactory().constructParametricType(
            ApiResponseDTO.class,
            String.class
        )
    );

    assertNotNull(apiResponse);
    assertEquals(200, apiResponse.status);
    assertNotNull(apiResponse.data);
    assertEquals(requestText, apiResponse.data);
  }

  @Test
  @Order(10)
  @DisplayName("Should return 200 when sending valid request DTO")
  void validateBody_validRequest_returns200() throws Exception {
    UserResponseDTO requestBody = new UserResponseDTO();
    requestBody.setUsername("testuser");
    requestBody.setPassword("securepassword");

    String requestBodyStr = MAPPER.writeValueAsString(requestBody);

    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/body/validate"))
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr))
            .header("Content-Type", "application/json")
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());

    ApiResponseDTO<UserResponseDTO> apiResponse = MAPPER.readValue(
        response.body(),
        MAPPER.getTypeFactory().constructParametricType(
            ApiResponseDTO.class,
            UserResponseDTO.class
        )
    );

    assertNotNull(apiResponse);
    assertEquals(200, apiResponse.status);
    assertNotNull(apiResponse.data);
    assertEquals(requestBody.getUsername(), apiResponse.data.getUsername());
  }

  @Test
  @Order(11)
  @DisplayName("Should return 400 when sending bad body")
  void validateBody_missingUsername_returns400() throws Exception {
    // Create a map directly since the controller expects a Map, not a DTO
    Map<String, String> requestBody = new HashMap<>();
    requestBody.put("password", "securepassword");

    String requestBodyStr = MAPPER.writeValueAsString(requestBody);

    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/body/validate"))
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr))
            .header("Content-Type", "application/json")
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertEquals(400, response.statusCode());

    ApiResponseDTO<Map<String, Object>> apiResponse = MAPPER.readValue(
        response.body(),
        MAPPER.getTypeFactory().constructParametricType(
            ApiResponseDTO.class,
            MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
        )
    );

    assertNotNull(apiResponse);
    assertEquals(400, apiResponse.status);
    assertNotNull(apiResponse.data);
    assertTrue(apiResponse.data.containsKey("error"), "Response data should contain 'error' field");
    assertEquals("Username is required", apiResponse.data.get("error"));
  }

  @Test
  @Order(12)
  @DisplayName("Should return 400 when sending empty body")
  void validateBody_emptyBody_returns400() throws Exception {
    // Send a request with an empty body
    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/body/validate"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .header("Content-Type", "application/json")
            .build(),
        HttpResponse.BodyHandlers.ofString());

    // Verify the response status code
    assertEquals(400, response.statusCode());

    try {
      // Try to parse the response as a map
      Map<String, Object> errorResponse = MAPPER.readValue(
          response.body(),
          MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
      );

      // If we get here, the response was a valid JSON object
      assertNotNull(errorResponse);
      assertTrue(errorResponse.containsKey("error") || errorResponse.containsKey("detail"),
          "Response should contain 'error' or 'detail' field");

      // Check for either error message format
      String errorMessage = errorResponse.containsKey("error")
          ? (String) errorResponse.get("error")
          : (String) errorResponse.get("detail");

      assertTrue(
          "Request body cannot be empty".equals(errorMessage) ||
              errorMessage.contains("No content to map"),
          "Unexpected error message: " + errorMessage
      );
    } catch (Exception _) {
      // If parsing as JSON fails, check if the response body contains the expected error message
      assertTrue(
          response.body().contains("Request body cannot be empty") ||
              response.body().contains("No content to map"),
          "Unexpected response body: " + response.body()
      );
    }
  }
}
