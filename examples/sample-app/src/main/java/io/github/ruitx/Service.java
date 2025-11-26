package io.github.ruitx;

import io.netty.handler.codec.http.FullHttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.CachedHttpResponse;
import org.nexus.NexusExecutor;
import org.nexus.NexusStaticResponseRegistry;
import org.nexus.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Service {

  private static final Logger LOGGER = LoggerFactory.getLogger(Service.class);

  static {
    NexusStaticResponseRegistry.register("echo", "OK", 200);
  }

  private final Repository repository;

  @Inject
  public Service(Repository repository) {
    this.repository = repository;
  }

  public Response<String> echo() {
    FullHttpResponse preComputed = NexusStaticResponseRegistry.get("echo");
    return new CachedHttpResponse(preComputed);
  }

  public CompletableFuture<Response<String>> getSample(String name) {
    return CompletableFuture.supplyAsync(
        () -> repository.getSample(name),
        NexusExecutor.INSTANCE.get()
    ).thenApply(count -> {
      String body = String.valueOf(count);
      return new Response<>(200, body);
    });
  }

  public CompletableFuture<Response<String>> postSample(String name) {
    return CompletableFuture.supplyAsync(
        () -> repository.postSample(name),
        NexusExecutor.INSTANCE.get()
    ).thenApply(inserted -> {
      String body = String.valueOf(inserted);
      return new Response<>(201, body);
    });
  }

}
