package org.nexus.controllers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.nexus.Response;
import org.nexus.annotations.Controller;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.RequestBody;
import org.nexus.annotations.Secured;
import org.nexus.dto.PostRequest;
import org.nexus.dto.TestDto;
import org.nexus.dto.Todo;
import org.nexus.dto.UserDto;
import org.nexus.enums.HttpMethod;
import org.nexus.services.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class ApiController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiController.class);
  private static final String ENDPOINT = "/api/v1";

  private final ApiService apiService;

  public ApiController(ApiService apiService) {
    this.apiService = apiService;
  }

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/db/:name")
  public CompletableFuture<Response<TestDto>> db(String name) {
    return apiService.db(name)
        .thenApply(data -> new Response<>(200, data));
  }

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/db-select/:name")
  public CompletableFuture<Response<TestDto>> dbSelect(String name) {
    return apiService.dbSelect(name)
        .thenApply(data -> new Response<>(200, data));
  }

  @Secured(permitAll = true)
  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/heartbeat")
  public CompletableFuture<Response<String>> pong() {
    return apiService.pong();
  }

  @Secured
  @Mapping(type = HttpMethod.POST, endpoint = ENDPOINT + "/post/:id")
  public CompletableFuture<Response<String>> testPOST(int id, @RequestBody PostRequest request) {
    return apiService.testPOST(id, request);
  }

  @Secured
  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/external-call")
  public CompletableFuture<Response<List<Todo>>> externalCall() {
    return apiService.externalCall();
  }

  @Secured
  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/user")
  public CompletableFuture<Response<UserDto>> getUser() {
    return apiService.getUser();
  }

}
