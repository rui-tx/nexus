package org.nexus;

import io.avaje.inject.BeanScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NexusBeanScope {

  private static final Logger LOGGER = LoggerFactory.getLogger(NexusBeanScope.class);


  private static volatile BeanScope scope;

  private NexusBeanScope() {
  }

  public static synchronized void init() {
    if (scope != null) {
      LOGGER.error("BeanScope already initialized");
      return;
    }

    scope = BeanScope.builder().build();
  }

  public static BeanScope get() {
    if (scope == null) {
      throw new IllegalStateException(
          "BeanScope not initialized. Call NexusBeanScope.init() during startup");
    }

    return scope;
  }

  public static synchronized void close() {
    if (scope == null) {
      LOGGER.error("BeanScope not initialized");
      return;
    }

    scope.close();
    scope = null;
  }
}
