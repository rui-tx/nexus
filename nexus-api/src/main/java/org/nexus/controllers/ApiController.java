package org.nexus.controllers;


import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.Secured;
import org.nexus.enums.HttpMethod;
import org.nexus.services.ApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ApiController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiController.class);
  private static final String ENDPOINT = "/api/v1";

  private final ApiService apiService;

  @Inject
  public ApiController(ApiService apiService) {
    this.apiService = apiService;
  }

  @Secured(permitAll = true)
  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/ping")
  public CompletableFuture<Response<String>> ping() {
    return apiService.pong();
  }
}
