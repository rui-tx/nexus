package org.nexus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.nexus.dto.ApiResponseDTO;
import org.nexus.dto.test.UserResponseDTO;

class EndToEndTests {

  private static final ObjectMapper MAPPER = NexusUtils.DF_MAPPER;
  private static TestNexusApplication app;
  private static HttpClient httpClient;
  private static String baseUrl;

  @BeforeAll
  static void setUp() {
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
  }

  @Test
  @Order(1)
  void testHealthEndpoint() throws Exception {
    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("status"), "Response should contain status field");
  }

  @Test
  @Order(2)
  void testNotFoundEndpoint() throws Exception {
    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/nonexistent"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );

    assertEquals(404, response.statusCode());
  }

  @Test
  @Order(3)
  void testUserRegistration() throws Exception {
    // Prepare test data
    Map<String, Object> userData = new HashMap<>();
    userData.put("username", "testuser");
    userData.put("password", "securepassword123");

    String requestBody = MAPPER.writeValueAsString(userData);

    // Send request
    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/users/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );

    // Verify response
    assertEquals(200, response.statusCode());

    // Parse and verify response body
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
    assertEquals("testuser", apiResponse.data.getUsername());
  }

  //TODO: fix this test by sending the right exception
  @Test
  @Order(4)
  void testInvalidUserRegistration() throws Exception {
    // Prepare invalid test data (missing password)
    Map<String, Object> userData = new HashMap<>();
    userData.put("username", "testuser");

    String requestBody = MAPPER.writeValueAsString(userData);

    // Send request
    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/users/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );

    // Verify validation error
    assertEquals(500, response.statusCode());

    // Verify error message
//    String responseBody = response.body();
//    assertTrue(responseBody.contains("error") || responseBody.contains("message"),
//        "Response should contain error information");
  }

  @Test
  @Order(5)
  void testCachedResponse() throws Exception {
    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/cache"))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );

    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("status"), "Response should contain status field");
  }
}
