package org.nexus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NexusExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(NexusExecutor.class);
  private static volatile boolean shutdown = false;

  private NexusExecutor() {
  }

  public static ExecutorService get() {
    return InstanceHolder.instance;
  }

  public static synchronized void shutdown() {
    if (!shutdown) {
      LOGGER.info("Shutting down NexusExecutor");
      InstanceHolder.instance.shutdown();
      try {
        if (!InstanceHolder.instance.awaitTermination(30, TimeUnit.SECONDS)) {
          InstanceHolder.instance.shutdownNow();
        }
      } catch (InterruptedException _) {
        InstanceHolder.instance.shutdownNow();
        Thread.currentThread().interrupt();
      }
      shutdown = true;
    }
  }

  private static final class InstanceHolder {

    private static final ExecutorService instance = Executors.newVirtualThreadPerTaskExecutor();
  }
}