package org.nexus.controllers;

import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.dto.PathParamResponseTestDTO;
import org.nexus.enums.HttpMethod;

@Singleton
public class SmokeTestsController {

  @Mapping(type = HttpMethod.GET, endpoint = "/found")
  public CompletableFuture<Response<String>> found_returns200() {
    return CompletableFuture.completedFuture(new Response<>(200, "found"));
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/throw")
  public CompletableFuture<Response<String>> unexpectedException_returns500() {
    throw new RuntimeException("boom");
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/path/:path1/:path2")
  public CompletableFuture<Response<PathParamResponseTestDTO>> sentCorrectPathParams_returns200(
      int pathParam1, String pathParam2) {
    return CompletableFuture.completedFuture(
        new Response<>(200, new PathParamResponseTestDTO(pathParam1, pathParam2)));
  }
}
