package org.nexus.services;

import io.netty.handler.codec.http.FullHttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.CachedHttpResponse;
import org.nexus.NexusStaticResponseRegistry;
import org.nexus.Response;
import org.nexus.repositories.ApiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ApiService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiService.class);

  // pre computed responses
  static {
    NexusStaticResponseRegistry.register("heartbeat", "up", 200);
  }

  private final ApiRepository apiRepository;

  @Inject
  public ApiService(ApiRepository apiRepository) {
    this.apiRepository = apiRepository;
  }

  public CompletableFuture<Response<String>> pong() {
    FullHttpResponse preComputed = NexusStaticResponseRegistry.get("heartbeat");
    return CompletableFuture.completedFuture(new CachedHttpResponse(preComputed));
  }

}
