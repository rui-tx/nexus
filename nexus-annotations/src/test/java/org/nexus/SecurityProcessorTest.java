package org.nexus;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.common.truth.Truth;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.nexus.annotations.processor.SecurityProcessor;

class SecurityProcessorTest {

  @Test
  void shouldGenerateDefaultSecurityRuleWithExactRoute() throws Exception {
    // Given: Controller
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.annotations.Secured;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            
            public class TestController {
                @Secured
                @Mapping(type = HttpMethod.GET, endpoint = "/test1")
                public CompletableFuture<Response<String>> route() {
                    return CompletableFuture.completedFuture(new Response(200, "OK"));
                }
            }
            """
    );

    // When: Compiling
    Compilation compilation = javac()
        .withProcessors(new SecurityProcessor())
        .compile(controllerSource);

    assertThat(compilation).succeeded();

    String generatedSource = compilation
        .generatedSourceFile("org.nexus.GeneratedSecurityRules")
        .orElseThrow()
        .getCharContent(true)
        .toString();

    // Remove extra whitespace for reliable comparison
    String normalized = generatedSource.replaceAll("\\s+", " ").trim();

    // Then: Should have created a default security rule
    Truth.assertThat(normalized)
        .contains(
            "public final class GeneratedSecurityRules implements SecurityResolver.SecurityRulesProvider");
    Truth.assertThat(normalized)
        .contains("exactRules.put(\"GET \" + PathMatcher.normalise(\"/test1\")");

    Truth.assertThat(normalized).containsMatch(
        "new SecurityRule\\( " +
            "\"org\\.nexus\\.test\\.TestController\", " +
            "\"route\", " +
            "\"GET\", " +
            "\"/test1\", " +
            "false, " +
            "Set\\.of\\(\\), " +
            "Set\\.of\\(\\) " +
            "\\)"
    );

    Truth.assertThat(generatedSource)
        .contains("public SecurityRule getRule(String httpMethod, String path)");
  }

  @Test
  void shouldGenerateDefaultSecurityRuleWithDynamicRoute() throws Exception {
    // Given: Controller
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.annotations.Secured;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            
            public class TestController {
                @Secured
                @Mapping(type = HttpMethod.GET, endpoint = "/test2/:path")
                public CompletableFuture<Response<String>> route(String path) {
                    return CompletableFuture.completedFuture(new Response(200, "OK"));
                }
            }
            """
    );

    // When: Compiling
    Compilation compilation = javac()
        .withProcessors(new SecurityProcessor())
        .compile(controllerSource);

    assertThat(compilation).succeeded();

    String generatedSource = compilation
        .generatedSourceFile("org.nexus.GeneratedSecurityRules")
        .orElseThrow()
        .getCharContent(true)
        .toString();

    // Remove extra whitespace for reliable comparison
    String normalized = generatedSource.replaceAll("\\s+", " ").trim();

    // Then: Should have created a default security rule
    Truth.assertThat(normalized)
        .contains(
            "public final class GeneratedSecurityRules implements SecurityResolver.SecurityRulesProvider");
    Truth.assertThat(normalized)
        .contains("dynamicRulesByMethod.computeIfAbsent(\"GET\", k -> new ArrayList<>())");

    Truth.assertThat(normalized).containsMatch(
        "new SecurityRule\\( " +
            "\"org\\.nexus\\.test\\.TestController\", " +
            "\"route\", " +
            "\"GET\", " +
            "\"/test2/:path\", " +
            "false, " +
            "Set\\.of\\(\\), " +
            "Set\\.of\\(\\) " +
            "\\)"
    );

    Truth.assertThat(generatedSource)
        .contains("public SecurityRule getRule(String httpMethod, String path)");
  }

  @Test
  void shouldGenerateCustomSecurityRuleWithExactRoute() throws Exception {
    // Given: Controller
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.annotations.Secured;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            
            public class TestController {
                @Secured(value = "ADMIN", permissions = {"R", "W"})
                @Mapping(type = HttpMethod.GET, endpoint = "/test3")
                public CompletableFuture<Response<String>> route() {
                    return CompletableFuture.completedFuture(new Response(200, "OK"));
                }
            }
            """
    );

    // When: Compiling
    Compilation compilation = javac()
        .withProcessors(new SecurityProcessor())
        .compile(controllerSource);

    assertThat(compilation).succeeded();

    String generatedSource = compilation
        .generatedSourceFile("org.nexus.GeneratedSecurityRules")
        .orElseThrow()
        .getCharContent(true)
        .toString();

    // Remove extra whitespace for reliable comparison
    String normalized = generatedSource.replaceAll("\\s+", " ").trim();

    // Then: Should have created a default security rule
    Truth.assertThat(normalized)
        .contains(
            "public final class GeneratedSecurityRules implements SecurityResolver.SecurityRulesProvider");
    Truth.assertThat(normalized)
        .contains("exactRules.put(\"GET \" + PathMatcher.normalise(\"/test3\")");

    Truth.assertThat(normalized).containsMatch(
        "new SecurityRule\\( " +
            "\"org\\.nexus\\.test\\.TestController\", " +
            "\"route\", " +
            "\"GET\", " +
            "\"/test3\", " +
            "false, " +
            "Set\\.of\\(\"ADMIN\"\\), " +
            "Set\\.of\\(\"[RW]\", \"[RW]\"\\) " +
            "\\)"
    );

    Truth.assertThat(generatedSource)
        .contains("public SecurityRule getRule(String httpMethod, String path)");
  }

  @Test
  void shouldGenerateCustomSecurityRuleWithDynamicRoute() throws Exception {
    // Given: Controller
    JavaFileObject controllerSource = JavaFileObjects.forSourceString(
        "org.nexus.test.TestController",
        """
            package org.nexus.test;
            
            import org.nexus.annotations.Mapping;
            import org.nexus.annotations.Secured;
            import org.nexus.enums.HttpMethod;
            import org.nexus.Response;
            import java.util.concurrent.CompletableFuture;
            
            public class TestController {
                @Secured(value = "ADMIN", permissions = {"R", "W"})
                @Mapping(type = HttpMethod.GET, endpoint = "/test4/:path")
                public CompletableFuture<Response<String>> route(String path) {
                    return CompletableFuture.completedFuture(new Response(200, "OK"));
                }
            }
            """
    );

    // When: Compiling
    Compilation compilation = javac()
        .withProcessors(new SecurityProcessor())
        .compile(controllerSource);

    assertThat(compilation).succeeded();

    String generatedSource = compilation
        .generatedSourceFile("org.nexus.GeneratedSecurityRules")
        .orElseThrow()
        .getCharContent(true)
        .toString();

    // Remove extra whitespace for reliable comparison
    String normalized = generatedSource.replaceAll("\\s+", " ").trim();

    // Then: Should have created a default security rule
    Truth.assertThat(normalized)
        .contains(
            "public final class GeneratedSecurityRules implements SecurityResolver.SecurityRulesProvider");
    Truth.assertThat(normalized)
        .contains("dynamicRulesByMethod.computeIfAbsent(\"GET\", k -> new ArrayList<>())");

    Truth.assertThat(normalized).containsMatch(
        "new SecurityRule\\( " +
            "\"org\\.nexus\\.test\\.TestController\", " +
            "\"route\", " +
            "\"GET\", " +
            "\"/test4/:path\", " +
            "false, " +
            "Set\\.of\\(\"ADMIN\"\\), " +
            "Set\\.of\\(\"[RW]\", \"[RW]\"\\) " +
            "\\)"
    );

    Truth.assertThat(generatedSource)
        .contains("public SecurityRule getRule(String httpMethod, String path)");
  }
}
