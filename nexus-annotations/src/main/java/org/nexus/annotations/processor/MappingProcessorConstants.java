package org.nexus.annotations.processor;

public final class MappingProcessorConstants {

  // Package and class names
  public static final String GENERATED_PACKAGE = "nexus.generated";
  public static final String GENERATED_FILE_NAME = "GeneratedRoutes";
  public static final String GENERATED_PACKAGE_FILE = GENERATED_PACKAGE + "." + GENERATED_FILE_NAME;
  // Code generation templates
  public static final String GENERATED_CLASS_HEADER = """
      package %s;
      
      import com.fasterxml.jackson.core.JsonProcessingException;
      import com.fasterxml.jackson.databind.ObjectMapper;
      import org.nexus.Route;
      import org.nexus.interfaces.ProblemDetails;
      import org.nexus.enums.ProblemDetailsTypes;
      import org.nexus.exceptions.ProblemDetailsException;
      import io.netty.handler.codec.http.HttpMethod;
      import java.util.*;
      import java.util.stream.Collectors;
      import java.util.concurrent.CompletableFuture;
      
      public final class %s {
        private static final Map<String, Route<?>> routeMap = new HashMap<>();
        private static final ObjectMapper MAPPER = new ObjectMapper();
      
        static {
          try {
            initRoutes();
          } catch (Exception e) {
            throw new RuntimeException(\"Failed to initialize routes\", e);
          }
        }
      """.formatted(GENERATED_PACKAGE, GENERATED_FILE_NAME);
  // Type names
  public static final String TYPE_STRING = "java.lang.String";
  public static final String TYPE_INT = "java.lang.Integer";
  public static final String TYPE_LONG = "java.lang.Long";
  public static final String LIST_TYPE = "java.util.List";
  public static final String COMPLETABLE_FUTURE = "java.util.concurrent.CompletableFuture";
  public static final String RESPONSE_TYPE = "org.nexus.Response";
  // Error messages
  public static final String ERROR_MAPPING_ONLY_ON_METHODS = "@Mapping can only be used on methods";
  public static final String ERROR_DUPLICATE_ROUTE = "Duplicate route: '%s' already defined in '%s'";
  public static final String GENERATED_CLASS_FOOTER = """
      
        public static Route<?> getRoute(String method, String path) {
          return routeMap.get(method + " " + path);
        }
      
        public static List<Route<?>> getRoutes() {
          return new ArrayList<>(routeMap.values());
        }
      
        public static List<Route<?>> getRoutes(String method) {
          return routeMap.entrySet().stream()
              .filter(e -> e.getKey().startsWith(method + " "))
              .map(Map.Entry::getValue)
              .collect(Collectors.toList());
        }
      
        public static String pathTemplate(Route<?> route) {
          return route.getPath();
        }
      }
      """;
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

  private MappingProcessorConstants() {
  }
}
