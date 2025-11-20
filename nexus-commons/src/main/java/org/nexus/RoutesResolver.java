package org.nexus;

import java.util.Map;
import java.util.ServiceLoader;

/**
 * RoutesResolver provides a stable indirection layer for resolving routes without a direct
 * compile-time dependency on the GeneratedRoutes class.
 * </p>
 * The user application (or any module that uses @Mapping) will generate org.nexus.GeneratedRoutes
 * at compile-time. This resolver will use ServiceLoader to load RoutesProvider at runtime.
 */
public final class RoutesResolver {

  private RoutesResolver() {
  }

  public static RouteMatch findMatchingRoute(String method, String path) {
    RoutesProvider p = ProviderHolder.INSTANCE;
    return p != null ? p.findMatchingRoute(method, path) : null;
  }

  public interface RoutesProvider {

    RouteMatch findMatchingRoute(String method, String path);
  }

  private static class ProviderHolder {

    static final RoutesProvider INSTANCE = loadProvider();

    private static RoutesProvider loadProvider() {
      ServiceLoader<RoutesProvider> loader = ServiceLoader.load(RoutesProvider.class);
      return loader.findFirst().orElse(null);
    }
  }

  public record RouteMatch(Route<?> route, Map<String, String> params) {

  }
}
