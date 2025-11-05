package org.nexus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple DI container for controller instances. Controllers are registered at application startup
 * and reused for all requests.
 */
public final class NexusDIRegistry {

  private static final NexusDIRegistry INSTANCE = new NexusDIRegistry();
  private final Map<Class<?>, Object> controllers = new ConcurrentHashMap<>();
  private volatile boolean locked = false;

  private NexusDIRegistry() {
  }

  public static NexusDIRegistry getInstance() {
    return INSTANCE;
  }

  public <T> void register(Class<T> controllerClass, T instance) {
    if (locked) {
      throw new IllegalStateException(
          "Registry is locked - controllers must be registered during startup");
    }
    if (instance == null) {
      throw new IllegalArgumentException("Controller instance cannot be null");
    }
    controllers.put(controllerClass, instance);
  }

  @SuppressWarnings("unchecked")
  public <T> T get(Class<T> controllerClass) {
    Object controller = controllers.get(controllerClass);
    if (controller == null) {
      throw new IllegalStateException(
          "Controller not registered: " + controllerClass.getName() +
              ". Did you forget to register it in Main.java?"
      );
    }
    return (T) controller;
  }

  /**
   * Lock the registry to prevent further registrations. Should be called after all controllers are
   * registered, before starting the server.
   */
  public void lock() {
    this.locked = true;
  }

  /**
   * Clear all registrations and unlock (useful for testing).
   */
  public void reset() {
    controllers.clear();
    locked = false;
  }

  public boolean isRegistered(Class<?> controllerClass) {
    return controllers.containsKey(controllerClass);
  }
}