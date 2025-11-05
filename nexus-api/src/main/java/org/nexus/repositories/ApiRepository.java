package org.nexus.repositories;

import java.util.concurrent.CompletableFuture;
import java.util.random.RandomGenerator;
import org.nexus.NexusExecutor;
import org.nexus.annotations.Repository;

@Repository
public class ApiRepository {

  public String getData() {
    CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
      try {
        // 'db' call
        Thread.sleep(RandomGenerator.getDefault().nextLong(0, 2500));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      return "data";
    }, NexusExecutor.INSTANCE.get());

    return future.join();
  }
}
