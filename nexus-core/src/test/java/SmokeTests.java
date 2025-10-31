import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.ApiResponseDTO;
import dto.PathParamRequestTestDTO;
import dto.PathParamResponseTestDTO;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nexus.Main;
import org.nexus.Response;
import org.nexus.handlers.testing.TestRouteRegistry;

class SmokeTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Main server;
  private HttpClient http;
  private String baseUrl;

  @BeforeEach
  void setUp() throws Exception {
    // note: the tests CAN reach the GeneratedRoutes table, these routes just have priority
    var testRoutes = new TestRouteRegistry()
        .get("/found", _ ->
            CompletableFuture.completedFuture(new Response<>(200, "found")))
        .get("/throw", _ ->
            CompletableFuture.failedFuture(new RuntimeException("boom")))
        .get("/path/:foo/:bar", rc -> {
          int foo = Integer.parseInt(rc.getPathParams().get("foo"));
          String bar = rc.getPathParams().get("bar");
          return CompletableFuture.completedFuture(
              new Response<>(200, new PathParamRequestTestDTO(foo, bar))
          );
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
    Integer pathParam1 = 1;
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
    assertEquals(pathParam1, api.data.foo);
    assertEquals(pathParam2, api.data.bar);
  }
}
