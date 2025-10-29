package org.nexus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public enum NexusExecutor {
  INSTANCE;

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public ExecutorService get() {
    return executor;
  }
}