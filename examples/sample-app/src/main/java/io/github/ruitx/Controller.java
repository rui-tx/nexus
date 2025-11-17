package io.github.ruitx;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusExecutor;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.enums.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Controller {

  private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

  private final Service service;

  @Inject
  public Controller(Service service) {
    this.service = service;
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/echo")
  public CompletableFuture<Response<String>> echo() {
    return CompletableFuture.supplyAsync(service::echo, NexusExecutor.INSTANCE.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/entry/:name")
  public CompletableFuture<Response<String>> getSample(String name) {
    return service.getSample(name);
  }

  @Mapping(type = HttpMethod.POST, endpoint = "/entry/:name")
  public CompletableFuture<Response<String>> postSample(String name) {
    return service.postSample(name);
  }
}
