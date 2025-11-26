package io.github.ruitx;

import static io.github.ruitx.Controller.API_VERSION;

import io.netty.handler.codec.http.FullHttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.CachedHttpResponse;
import org.nexus.NexusExecutor;
import org.nexus.NexusStaticResponseRegistry;
import org.nexus.Response;

@Singleton
public class Service {

  static {
    NexusStaticResponseRegistry.register(
        "ping-pong",
        new ApiVersion("sample-app", API_VERSION), 200
    );
  }

  private final Repository repository;

  @Inject
  public Service(Repository repository) {
    this.repository = repository;
  }

  public Response<String> pong() {
    FullHttpResponse preComputed = NexusStaticResponseRegistry.get("ping-pong");
    return new CachedHttpResponse<>(preComputed);
  }

  public CompletableFuture<Response<String>> getSample(String name) {
    return CompletableFuture.supplyAsync(
        () -> repository.getSample(name),
        NexusExecutor.get()
    ).thenApply(count -> {
      String body = String.valueOf(count);
      return new Response<>(200, body);
    });
  }

  public CompletableFuture<Response<String>> postSample(String name) {
    return CompletableFuture.supplyAsync(
        () -> repository.postSample(name),
        NexusExecutor.get()
    ).thenApply(inserted -> {
      String body = String.valueOf(inserted);
      return new Response<>(201, body);
    });
  }

  public record ApiVersion(String name, String version) {

  }
}
