package org.nexus;

import java.util.ServiceLoader;
import java.util.Map;

/**
 * RoutesResolver provides a stable indirection layer for resolving routes
 * without a direct compile-time dependency on the GeneratedRoutes class.
 *
 * The user application (or any module that uses @Mapping) will generate
 * org.nexus.GeneratedRoutes at compile-time. This resolver will use
 * ServiceLoader to load RoutesProvider at runtime.
 */
public final class RoutesResolver {

  private static volatile RoutesProvider provider;

  private RoutesResolver() { }

  private static RoutesProvider load() {
    RoutesProvider p = provider;
    if (p != null) return p;
    synchronized (RoutesResolver.class) {
      if (provider != null) return provider;
      ServiceLoader<RoutesProvider> loader = ServiceLoader.load(RoutesProvider.class);
      for (RoutesProvider rp : loader) {
        provider = rp;
        break;
      }
      return provider;
    }
  }

  public static RouteMatch findMatchingRoute(String method, String path) {
    RoutesProvider p = load();
    return (p == null) ? null : p.findMatchingRoute(method, path);
  }

  public interface RoutesProvider {
    RouteMatch findMatchingRoute(String method, String path);
  }

  public record RouteMatch(Route<?> route, Map<String, String> params) { }
}
