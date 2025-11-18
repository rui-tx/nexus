import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.nexus.NexusBeanScope;
import org.nexus.config.ServerConfig;
import org.nexus.dto.ApiResponseDTO;
import org.nexus.dto.PathParamResponseTestDTO;
import org.nexus.dto.test.QueryResponseDTO;
import org.nexus.dto.test.UserResponseDTO;
import org.nexus.server.NexusServer;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Nexus Core Smoke Tests")
class SmokeTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private NexusServer server;
  private HttpClient http;
  private String baseUrl;

  @BeforeAll
  static void init() {
    // Initialize DI scope
    NexusBeanScope.init();
  }

  @BeforeEach
  void setUp() throws Exception {

    ServerConfig cfg = ServerConfig.builder()
        .bindAddress("127.0.0.1")
        .port(0)
        .idleTimeoutSeconds(300)
        .maxContentLength(1_048_576)
        .build();

    server = new NexusServer(cfg, List.of());
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getPort();
    http = HttpClient.newHttpClient();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  @Order(1)
  @DisplayName("Should return 200 for a existing endpoint")
  void found_returns200() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/found")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("found"));
  }

  @Test
  @Order(2)
  @DisplayName("Should return 404 for a non-existing endpoint")
  void notFound_returns404() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/not-found")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(404, res.statusCode());
  }

  @Test
  @Order(3)
  @DisplayName("Should return 500 for an unexcepted error")
  void unexpectedException_returns500() throws Exception {
    HttpResponse<String> res = http.send(
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

    HttpResponse<String> response = http.send(
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

    HttpResponse<String> res = http.send(
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

    HttpResponse<String> response = http.send(
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

    HttpResponse<String> response = http.send(
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

    HttpResponse<String> response = http.send(
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

    HttpResponse<String> response = http.send(
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

    HttpResponse<String> response = http.send(
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

    HttpResponse<String> response = http.send(
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
    HttpResponse<String> response = http.send(
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
    } catch (Exception e) {
      // If parsing as JSON fails, check if the response body contains the expected error message
      assertTrue(
          response.body().contains("Request body cannot be empty") ||
              response.body().contains("No content to map"),
          "Unexpected response body: " + response.body()
      );
    }
  }
}
