package org.nexus.annotations.processor;

public final class MappingProcessorConstants {

  public static final String GENERATED_PACKAGE = "nexus.generated";
  public static final String GENERATED_FILE_NAME = "GeneratedRoutes";
  public static final String GENERATED_PACKAGE_FILE = GENERATED_PACKAGE + "." + GENERATED_FILE_NAME;

  public static final String TYPE_STRING = "java.lang.String";
  public static final String TYPE_INT = "java.lang.Integer";
  public static final String TYPE_LONG = "java.lang.Long";
  public static final String LIST_TYPE = "java.util.List";
  public static final String COMPLETABLE_FUTURE = "java.util.concurrent.CompletableFuture";
  public static final String RESPONSE_TYPE = "org.nexus.Response";
  public static final String ERROR_MAPPING_ONLY_ON_METHODS = "@Mapping can only be used on methods";
  public static final String ERROR_DUPLICATE_ROUTE = "Duplicate route: '%s' already defined in '%s'";

  public static final String GENERATED_CLASS_HEADER = """
      package %s;
      
      import static org.nexus.NexusUtils.DF_MAPPER;
      
      import com.fasterxml.jackson.core.JsonProcessingException;
      import io.netty.handler.codec.http.HttpMethod;
      import java.util.*;
      import java.util.concurrent.CompletableFuture;
      import org.nexus.PathMatcher;
      import org.nexus.PathMatcher.CompiledPattern;
      import org.nexus.PathMatcher.Result;
      import org.nexus.Route;
      import org.nexus.enums.ProblemDetailsTypes;
      import org.nexus.exceptions.ProblemDetailsException;
      import org.nexus.interfaces.ProblemDetails;
      
      public final class %s {
        private static final Map<String, Route<?>> exactRoutes = new HashMap<>();
        private static final Map<String, List<CompiledRoute>> dynamicRoutesByMethod = new HashMap<>();
      
        private record CompiledRoute(CompiledPattern pattern, Route<?> route) {}
        public record RouteMatch(Route<?> route, Map<String, String> params) {}  // Changed to public
      
        static {
          initRoutes();
        }
      """.formatted(GENERATED_PACKAGE, GENERATED_FILE_NAME);

  public static final String HELPER_METHODS = """
      
        private static Integer safeParseInt(String value, String paramName, String endpoint) {
          if (value == null) return null;
          try {
              return Integer.valueOf(value);
          } catch (NumberFormatException e) {
            throw new ProblemDetailsException(
              new ProblemDetails.Single(
                ProblemDetailsTypes.PATH_PARAM_INVALID_INTEGER,
                "Invalid integer parameter",
                400,
                "Invalid integer for path parameter",
                endpoint,
                Map.of("field", paramName)
              )
            );
          }
        }
      
        private static Long safeParseLong(String value, String paramName, String endpoint) {
          if (value == null) return null;
          try {
            return Long.valueOf(value);
          } catch (NumberFormatException e) {
            throw new ProblemDetailsException(
              new ProblemDetails.Single(
                ProblemDetailsTypes.PATH_PARAM_INVALID_LONG,
                "Invalid long parameter",
                400,
                "Invalid long for path parameter",
                endpoint,
                Map.of("field", paramName)
              )
            );
          }
        }
      
        private static Integer safeParseIntQuery(String value, String paramName, String endpoint) {
          if (value == null) return null;
          try { return Integer.valueOf(value); }
          catch (NumberFormatException e) {
            throw new ProblemDetailsException(
              new ProblemDetails.Single(
                ProblemDetailsTypes.QUERY_PARAM_INVALID_INTEGER,
                "Invalid integer parameter",
                400,
                "Invalid integer for query parameter",
                endpoint,
                Map.of("field", paramName)
              )
            );
          }
        }
      
        private static Long safeParseLongQuery(String value, String paramName, String endpoint) {
          if (value == null) return null;
          try { return Long.valueOf(value); }
          catch (NumberFormatException e) {
            throw new ProblemDetailsException(
              new ProblemDetails.Single(
                ProblemDetailsTypes.QUERY_PARAM_INVALID_LONG,
                "Invalid long parameter",
                400,
                "Invalid long for query parameter",
                endpoint,
                Map.of("field", paramName)
              )
            );
          }
        }
      
        private static String requireQueryParam(String value, String name, String endpoint) {
          if (value != null && !value.isEmpty()) return value;
          throw new ProblemDetailsException(
            new ProblemDetails.Single(
              ProblemDetailsTypes.QUERY_PARAM_MISSING,
              "Missing required query parameter",
              400,
              "The required query parameter is missing",
              endpoint,
              Map.of("field", name)
            )
          );
        }
      """;

  public static final String GENERATED_CLASS_FOOTER = """
      
        public static RouteMatch findMatchingRoute(String httpMethod, String path) {
          String normPath = PathMatcher.normalise(path);
          String key = httpMethod.toUpperCase() + " " + normPath;
          Route<?> exact = exactRoutes.get(key);
          if (exact != null) {
            return new RouteMatch(exact, Map.of());
          }
          List<CompiledRoute> candidates = dynamicRoutesByMethod.get(httpMethod.toUpperCase());
          if (candidates == null) {
            return null;
          }
          for (CompiledRoute cr : candidates) {
            Result result = cr.pattern.match(normPath);
            if (result.matches()) {
              return new RouteMatch(cr.route, result.params());
            }
          }
          return null;
        }
      
        // Optional: Keep if you need these for other parts of the code
        public static List<Route<?>> getRoutes(String method) {
          List<Route<?>> routes = new ArrayList<>();
          String m = method.toUpperCase();
          exactRoutes.entrySet().stream()
              .filter(e -> e.getKey().startsWith(m + " "))
              .map(Map.Entry::getValue)
              .forEach(routes::add);
          List<CompiledRoute> dynamics = dynamicRoutesByMethod.get(m);
          if (dynamics != null) {
            dynamics.stream().map(CompiledRoute::route).forEach(routes::add);
          }
          return routes;
        }
      }
      """;

  private MappingProcessorConstants() {
  }
}