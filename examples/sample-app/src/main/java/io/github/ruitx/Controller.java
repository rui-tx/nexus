package io.github.ruitx;

import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusExecutor;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.enums.HttpMethod;

@Singleton
public class Controller {
  @Mapping(type = HttpMethod.GET, endpoint = "/ping")
  public CompletableFuture<Response<String>> ping() {
    return CompletableFuture.supplyAsync(
        () -> new Response<>(200, "OK"),
        NexusExecutor.INSTANCE.get());
  }
}
