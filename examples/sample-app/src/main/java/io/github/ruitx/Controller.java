package io.github.ruitx;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusExecutor;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.enums.HttpMethod;

@Singleton
public class Controller {

  public static final String API_VERSION = "v1";

  private static final String BASE_URL = "/api/" + API_VERSION;
  private final Service service;

  @Inject
  public Controller(Service service) {
    this.service = service;
  }

  @Mapping(type = HttpMethod.GET, endpoint = BASE_URL)
  public CompletableFuture<Response<String>> ping() {
    return CompletableFuture.supplyAsync(service::pong, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = BASE_URL + "/entry/:name")
  public CompletableFuture<Response<String>> getSample(String name) {
    return service.getSample(name);
  }

  @Mapping(type = HttpMethod.POST, endpoint = BASE_URL + "/entry/:name")
  public CompletableFuture<Response<String>> postSample(String name) {
    return service.postSample(name);
  }
}
