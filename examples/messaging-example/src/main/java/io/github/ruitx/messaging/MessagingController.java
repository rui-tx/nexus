package io.github.ruitx.messaging;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import org.nexus.NexusExecutor;
import org.nexus.Response;
import org.nexus.annotations.Mapping;
import org.nexus.enums.HttpMethod;
import org.nexus.exceptions.QueueFullException;

@Singleton
public class MessagingController {

  private static final String BASE_URL = "/api/v1/messaging";
  private final MessagingDemo messagingDemo;

  @Inject
  public MessagingController(MessagingDemo messagingDemo) {
    this.messagingDemo = messagingDemo;
  }

  @Mapping(type = HttpMethod.POST, endpoint = BASE_URL + "/send/:value")
  public CompletableFuture<Response<String>> send(String value) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        messagingDemo.send("key-" + System.nanoTime(), value);
        return new Response<>(202, "queued");
      } catch (QueueFullException e) {
        return new Response<>(503, "queue full");
      }
    }, NexusExecutor.get());
  }

  @Mapping(type = HttpMethod.GET, endpoint = BASE_URL + "/count")
  public CompletableFuture<Response<String>> count() {
    return CompletableFuture.supplyAsync(() -> {
      int processed = messagingDemo.getProcessedCount();
      return new Response<>(200, String.valueOf(processed));
    }, NexusExecutor.get());
  }
}
