package org.nexus;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.nexus.annotations.processor.MappingProcessor;

class MappingProcessorTest {

  @Test
  void shouldGenerateExactRoute() throws IOException {
    // Given: Controller
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            import java.util.List;
            
            public class TestController {
                @Mapping(type = HttpMethod.GET, endpoint = "/route")
                public CompletableFuture<Response<String>> route() {
                    return CompletableFuture.completedFuture(new Response(200, "OK"));
                }
            }
            """
    );

    // When: Compiling
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(controllerSource);
    assertThat(compilation).succeeded();

    String generatedSource = compilation
        .generatedSourceFile("org.nexus.GeneratedRoutes")
        .orElseThrow(() -> new AssertionError("GeneratedRoutes.java was not generated"))
        .getCharContent(true)
        .toString();

    // Then: Should have created an exact route entry
    assertThat(generatedSource).contains("""
        private static void initRoutes() {
            exactRoutes.put("GET " + PathMatcher.normalise("/route"), new Route<java.lang.String>(HttpMethod.GET, "/route", rc -> {
                        org.nexus.test.TestController controller = org.nexus.NexusBeanScope.get().get(org.nexus.test.TestController.class);
                      try {
                        return controller.route();
                      } catch (Exception e) {
                        return CompletableFuture.failedFuture(e); }}));
        
          }
        """);
  }

  @Test
  void shouldGenerateDynamicRoute() throws IOException {
    // Given: Controller
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            import java.util.List;
            
            public class TestController {
                @Mapping(type = HttpMethod.GET, endpoint = "/entry/:pathParam1")
                public CompletableFuture<Response<String>> route(String pathParam1) {
                  return CompletableFuture.completedFuture(new Response(200, pathParam1));
                }
            }
            """
    );

    // When: Compiling
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(controllerSource);
    assertThat(compilation).succeeded();

    String generatedSource = compilation
        .generatedSourceFile("org.nexus.GeneratedRoutes")
        .orElseThrow(() -> new AssertionError("GeneratedRoutes.java was not generated"))
        .getCharContent(true)
        .toString();

    // Then: Should have created a dynamic route entry
    assertThat(generatedSource).contains("""
        private static void initRoutes() {
            dynamicRoutesByMethod.computeIfAbsent("GET", k -> new ArrayList<>())
                .add(new CompiledRoute(CompiledPattern.compile("/entry/:pathParam1"),
                    new Route<java.lang.String>(HttpMethod.GET, "/entry/:pathParam1", rc -> {
                      java.lang.String pathParam1 = rc.getPathParams().get("pathParam1");  org.nexus.test.TestController controller = org.nexus.NexusBeanScope.get().get(org.nexus.test.TestController.class);
                      try {
                        return controller.route(pathParam1);
                      } catch (Exception e) {
                        return CompletableFuture.failedFuture(e); }})));
        
          }
        """);
  }

  @Test
  void shouldGenerateDynamicRouteWithMultiplePathParams() throws IOException {
    // Given: Controller
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            import java.util.List;
            
            public class TestController {
                @Mapping(type = HttpMethod.GET, endpoint = "/entry/:pathParam1/:pathParam2")
                public CompletableFuture<Response<String>> route(String pathParam1, Integer pathParam2) {
                  return CompletableFuture.completedFuture(new Response(200, pathParam1));
                }
            }
            """
    );

    // When: Compiling
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(controllerSource);
    assertThat(compilation).succeeded();

    String generatedSource = compilation
        .generatedSourceFile("org.nexus.GeneratedRoutes")
        .orElseThrow(() -> new AssertionError("GeneratedRoutes.java was not generated"))
        .getCharContent(true)
        .toString();

    // Then: Should have created a dynamic route entry with multiple params
    assertThat(generatedSource).contains("""
          private static void initRoutes() {
            dynamicRoutesByMethod.computeIfAbsent("GET", k -> new ArrayList<>())
                .add(new CompiledRoute(CompiledPattern.compile("/entry/:pathParam1/:pathParam2"),
                    new Route<java.lang.String>(HttpMethod.GET, "/entry/:pathParam1/:pathParam2", rc -> {
                      java.lang.String pathParam1 = rc.getPathParams().get("pathParam1");java.lang.Integer pathParam2 = safeParseInt(rc.getPathParams().get("pathParam2"), "pathParam2", "/entry/:pathParam1/:pathParam2");  org.nexus.test.TestController controller = org.nexus.NexusBeanScope.get().get(org.nexus.test.TestController.class);
                      try {
                        return controller.route(pathParam1, pathParam2);
                      } catch (Exception e) {
                        return CompletableFuture.failedFuture(e); }})));
        
          }
        """);
  }

  @Test
  void shouldGenerateExactRouteWithMultipleQueryParams() throws IOException {
    // Given: Controller
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.annotations.QueryParam;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            import java.util.List;
            
            public class TestController {
              @Mapping(type = HttpMethod.GET, endpoint = "/entry")
                public CompletableFuture<Response<String>> route(
                    @QueryParam("queryParam1") String queryParam1,
                    @QueryParam("queryParam2") Integer queryParam2) {
                  return CompletableFuture.completedFuture(new Response(200, queryParam1 + queryParam2));
                }
            }
            """
    );

    // When: Compiling
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(controllerSource);
    assertThat(compilation).succeeded();

    String generatedSource = compilation
        .generatedSourceFile("org.nexus.GeneratedRoutes")
        .orElseThrow(() -> new AssertionError("GeneratedRoutes.java was not generated"))
        .getCharContent(true)
        .toString();

    // Then: Should have created an exact route entry with multiple query params
    assertThat(generatedSource).contains("""
        private static void initRoutes() {
            exactRoutes.put("GET " + PathMatcher.normalise("/entry"), new Route<java.lang.String>(HttpMethod.GET, "/entry", rc -> {
                      java.lang.String queryParam1 = rc.getQueryParam("queryParam1");java.lang.Integer queryParam2 = safeParseIntQuery(rc.getQueryParam("queryParam2"), "queryParam2", "/entry");  org.nexus.test.TestController controller = org.nexus.NexusBeanScope.get().get(org.nexus.test.TestController.class);
                      try {
                        return controller.route(queryParam1, queryParam2);
                      } catch (Exception e) {
                        return CompletableFuture.failedFuture(e); }}));
        
          }
        """);
  }

  @Disabled
  @Test
  void shouldGenerateDynamicRouteWithMultipleQueryParams() throws IOException {
    // Given: Controller
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.annotations.QueryParam;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            import java.util.List;
            
            public class TestController {
              @Mapping(type = HttpMethod.GET, endpoint = "/entry/:pathParam1")
              public CompletableFuture<Response<String>> route(String pathParam1,
                  @QueryParam("queryParam1") String queryParam1,
                  @QueryParam("queryParam2") Integer queryParam2) {
                return CompletableFuture.completedFuture(new Response(200, pathParam1 + queryParam1 + queryParam2));
              }
            }
            """
    );

    // When: Compiling
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(controllerSource);
    assertThat(compilation).succeeded();

    String generatedSource = compilation
        .generatedSourceFile("org.nexus.GeneratedRoutes")
        .orElseThrow(() -> new AssertionError("GeneratedRoutes.java was not generated"))
        .getCharContent(true)
        .toString();

    // Then: Should have created a dynamic route entry with multiple query params
    assertThat(generatedSource).contains("""
        private static void initRoutes() {
          dynamicRoutesByMethod.computeIfAbsent("GET", k -> new ArrayList<>())
              .add(new CompiledRoute(CompiledPattern.compile("/entry/:pathParam1"),
                  new Route<java.lang.String>(HttpMethod.GET, "/entry/:pathParam1", rc -> {
                    java.lang.String pathParam1 = rc.getPathParams().get("pathParam1");java.lang.String queryParam1 = rc.getQueryParam("queryParam1");java.lang.Integer queryParam2 = safeParseIntQuery(rc.getQueryParam("queryParam2"), "queryParam2", "/entry/:pathParam1");  org.nexus.test.TestController controller = org.nexus.NexusBeanScope.get().get(org.nexus.test.TestController.class);
                    try {
                      return controller.route(pathParam1, queryParam1, queryParam2);
                    } catch (Exception e) {
                      return CompletableFuture.failedFuture(e); }})));
        
          }
        """);
  }

  @Test
  void shouldGenerateRoutesForSimpleGetMapping() {
    // Given: A controller with a simple GET mapping
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            
            public class TestController {
                @Mapping(type = HttpMethod.GET, endpoint = "/api/hello")
                public CompletableFuture<Response<String>> hello() {
                    return CompletableFuture.completedFuture(new Response(200, "Hello"));
                }
            }
            """
    );

    // When: Compile with the annotation processor
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(controllerSource);

    // Then: Compilation should succeed and generate the expected file
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("org.nexus.GeneratedRoutes")
        .contentsAsUtf8String()
        .contains("exactRoutes.put(\"GET \" + PathMatcher.normalise(\"/api/hello\")");
  }

  @Test
  void shouldGenerateRoutesForPathVariables() {
    // Given: A controller with path variables
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.UserController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            
            public class UserController {
                @Mapping(type = HttpMethod.GET, endpoint = "/api/users/:id")
                public CompletableFuture<Response<String>> getUser(
                    Long userId) {
                    return CompletableFuture.completedFuture(new Response(200, "User " + userId));
                }
            }
            """
    );

    // When: Compile with the annotation processor
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(controllerSource);

    // Then: Should generate dynamic route
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("org.nexus.GeneratedRoutes")
        .contentsAsUtf8String()
        .contains("dynamicRoutesByMethod.computeIfAbsent(\"GET\"");
    assertThat(compilation)
        .generatedSourceFile("org.nexus.GeneratedRoutes")
        .contentsAsUtf8String()
        .contains("CompiledPattern.compile(\"/api/users/:id\")");
  }

  @Test
  void shouldFailWhenMappingUsedOnNonMethod() {
    // Given: @Mapping annotation on a class (invalid)
    JavaFileObject invalidSource = JavaFileObjects.forSourceString(
        "org.nexus.test.InvalidController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.enums.HttpMethod;
            
            @Mapping(type = HttpMethod.GET, endpoint = "/api/test")
            public class InvalidController {
                public void someMethod() {}
            }
            """
    );

    // When: Compile with the annotation processor
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(invalidSource);

    // Then: Should fail with error
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("annotation interface not applicable to this kind of declaration");
  }

  @Test
  void shouldDetectDuplicateRoutes() {
    // Given: Two methods with the same route
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.DuplicateController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            
            public class DuplicateController {
                @Mapping(type = HttpMethod.GET, endpoint = "/api/test")
                public CompletableFuture<Response<String>> test1() {
                    return CompletableFuture.completedFuture(Response.ok("Test 1"));
                }
            
                @Mapping(type = HttpMethod.GET, endpoint = "/api/test")
                public CompletableFuture<Response<String>> test2() {
                    return CompletableFuture.completedFuture(Response.ok("Test 2"));
                }
            }
            """
    );

    // When: Compile with the annotation processor
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(controllerSource);

    // Then: Should fail with duplicate route error
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Duplicate route");
  }

  @Test
  void shouldValidateReturnType() {
    // Given: A method with invalid return type
    JavaFileObject invalidSource = JavaFileObjects.forSourceString(
        "org.nexus.test.InvalidReturnController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.enums.HttpMethod;
            
            public class InvalidReturnController {
                @Mapping(type = HttpMethod.GET, endpoint = "/api/test")
                public String test() {
                    return "Invalid";
                }
            }
            """
    );

    // When: Compile with the annotation processor
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(invalidSource);

    // Then: Should fail with return type error
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Return type must be `CompletableFuture<Response<T>>`");
  }

  @Disabled
  @Test
  void shouldGenerateEmptyRoutesWhenNoMappings() {
    // Given: A class without any @Mapping annotations
    JavaFileObject emptySource = JavaFileObjects.forSourceString(
        "org.nexus.test.EmptyController",
        """
            package org.nexus.test;
            
            public class EmptyController {
                public void someMethod() {}
            }
            """
    );

    // When: Compile with the annotation processor
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(emptySource);

    // Then: Should still generate routes file (empty)
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("org.nexus.GeneratedRoutes");
  }

  @Test
  void shouldHandleRequestBodyParameter() {
    // Given: A controller with @RequestBody parameter
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.PostController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.annotations.RequestBody;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            
            public class PostController {
                @Mapping(type = HttpMethod.POST, endpoint = "/api/users")
                public CompletableFuture<Response<User>> createUser(
                    @RequestBody User user) {
                    return CompletableFuture.completedFuture(new Response<>(200, user));
                }
            }
            """
    );

    JavaFileObject dtoSource = JavaFileObjects.forSourceString(
        "org.nexus.test.User",
        """
            package org.nexus.test;
            
            public record User (String name) {
            }
            """
    );

    // When: Compile with the annotation processor
    Compilation compilation = javac()
        .withProcessors(new MappingProcessor())
        .compile(dtoSource, controllerSource);

    // Then: Should generate route with RequestBody handling
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("org.nexus.GeneratedRoutes")
        .contentsAsUtf8String()
        .contains("DF_MAPPER.readValue");
  }

}