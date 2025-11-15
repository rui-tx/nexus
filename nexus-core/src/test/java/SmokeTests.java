import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nexus.NexusBeanScope;
import org.nexus.config.ServerConfig;
import org.nexus.dto.ApiResponseDTO;
import org.nexus.dto.PathParamResponseTestDTO;
import org.nexus.middleware.LoggingMiddleware;
import org.nexus.server.NexusServer;

class SmokeTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private NexusServer server;
  private HttpClient http;
  private String baseUrl;

  @BeforeEach
  void setUp() throws Exception {
    // Initialize DI scope (no beans required but NexusServer expects BeanScope)
    NexusBeanScope.init();

    ServerConfig cfg = ServerConfig.builder()
        .bindAddress("127.0.0.1")
        .port(0)
        .idleTimeoutSeconds(300)
        .maxContentLength(1_048_576)
        .build();

    server = new NexusServer(cfg, List.of(new LoggingMiddleware()));
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getPort();
    http = HttpClient.newHttpClient();
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void found_returns200() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/found")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("found"));
  }

  @Test
  void notFound_returns404() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/not-found")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(404, res.statusCode());
  }

  @Test
  void unexpectedException_returns500() throws Exception {
    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/throw")).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(500, res.statusCode());
  }

  @Test
  void sentCorrectPathParams_returns200() throws Exception {
    int pathParam1 = 1;
    String pathParam2 = "john";

    HttpResponse<String> res = http.send(
        HttpRequest.newBuilder(
                URI.create(baseUrl + "/path/%d/%s"
                    .formatted(pathParam1, pathParam2)))
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
}
