package org.nexus.annotations.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.nexus.SecurityRule;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.Secured;

@SupportedAnnotationTypes("org.nexus.annotations.Secured")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public final class SecurityProcessor extends AbstractProcessor {

  private static final String GENERATED_PACKAGE = "nexus.generated";
  private static final String CONFIG_CLASS = "GeneratedSecurityRules";

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      return false;
    }

    try {
      Set<SecurityRule> rules = new HashSet<>();
      for (Element element : roundEnv.getElementsAnnotatedWith(Secured.class)) {
        processElement(element, rules);
      }

      // Generate the security configuration
      if (!rules.isEmpty()) {
        generateSecurityConfig(rules);
      }
    } catch (Exception e) {
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "Failed to process @Secured annotations: " + e.getMessage()
      );
    }

    return true;
  }

  private void processElement(Element element, Set<SecurityRule> rules) {
    Secured secured = element.getAnnotation(Secured.class);
    if (secured == null) {
      return;
    }

    Mapping mapping = element.getAnnotation(Mapping.class);
    if (mapping == null) {
      String className = ((TypeElement) element.getEnclosingElement()).getQualifiedName()
          .toString();
      String methodName = element.getSimpleName().toString();
      processingEnv.getMessager().printMessage(
          Diagnostic.Kind.ERROR,
          "@Secured used without @Mapping on method: " + className + "." + methodName
      );
      return;
    }

    // Get class and method names
    String className = ((TypeElement) element.getEnclosingElement()).getQualifiedName().toString();
    String methodName = element.getSimpleName().toString();

    // Extract from Mapping
    String httpMethod = mapping.type().name();
    String endpoint = mapping.endpoint();

    // Create and add the security rule
    SecurityRule rule = new SecurityRule(
        className,
        methodName,
        httpMethod,
        endpoint,
        secured.permitAll(),
        Set.of(secured.value()),
        Set.of(secured.permissions())
    );
    rules.add(rule);
  }

  private void generateSecurityConfig(Set<SecurityRule> rules) throws IOException {
    String className = CONFIG_CLASS;
    JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(
        GENERATED_PACKAGE + "." + className);

    try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
      // Collect generated add statements
      StringBuilder addsBuilder = new StringBuilder();
      for (SecurityRule rule : rules) {
        String quotedRoles = toQuotedList(rule.requiredRoles());
        String quotedPermissions = toQuotedList(rule.requiredPermissions());
        String httpMethodUpper = rule.httpMethod().toUpperCase();
        String endpoint = rule.endpoint();
        boolean isDynamic = isDynamic(endpoint);

        String ruleCreation = """
            new SecurityRule(
              "%s",
              "%s",
              "%s",
              "%s",
              %b,
              Set.of(%s),
              Set.of(%s)
            )
            """.formatted(
            rule.className(),
            rule.methodName(),
            rule.httpMethod(),
            endpoint,
            rule.permitAll(),
            quotedRoles.isEmpty() ? "" : quotedRoles,
            quotedPermissions.isEmpty() ? "" : quotedPermissions
        );

        if (!isDynamic) {
          addsBuilder.append("""
              exactRules.put("%s " + PathMatcher.normalise("%s"), %s);
              """.formatted(
              httpMethodUpper,
              endpoint,
              ruleCreation.trim()
          ));
        } else {
          addsBuilder.append("""
              dynamicRulesByMethod.computeIfAbsent("%s", k -> new ArrayList<>()).add(
                new CompiledSecurityRule(
                  PathMatcher.CompiledPattern.compile("%s"),
                  %s
                )
              );
              """.formatted(
              httpMethodUpper,
              endpoint,
              ruleCreation.trim()
          ));
        }
      }

      String generatedCode = """
          package %s;
          
          import java.util.*;
          import org.nexus.PathMatcher;
          import org.nexus.PathMatcher.CompiledPattern;
          import org.nexus.PathMatcher.Result;
          import org.nexus.SecurityRule;
          
          public final class %s {
            private static final Map<String, SecurityRule> exactRules = new HashMap<>();
            private static final Map<String, List<CompiledSecurityRule>> dynamicRulesByMethod = new HashMap<>();
          
            private record CompiledSecurityRule(CompiledPattern pattern, SecurityRule rule) {}
          
            static {
              %s
            }
          
            public static SecurityRule getRule(String httpMethod, String path) {
              String normPath = PathMatcher.normalise(path);
              String key = httpMethod.toUpperCase() + " " + normPath;
              SecurityRule exact = exactRules.get(key);
              if (exact != null) {
                return exact;
              }
              List<CompiledSecurityRule> candidates = dynamicRulesByMethod.get(httpMethod.toUpperCase());
              if (candidates == null) {
                return null;
              }
              for (CompiledSecurityRule cr : candidates) {
                Result result = cr.pattern.match(normPath);
                if (result.matches()) {
                  return cr.rule;
                }
              }
              return null;
            }
          }
          """.formatted(
          GENERATED_PACKAGE,
          className,
          addsBuilder.toString()
      );

      out.print(generatedCode);
    }
  }

  private boolean isDynamic(String endpoint) {
    return endpoint.contains(":");
  }

  private String toQuotedList(Collection<String> items) {
    if (items.isEmpty()) {
      return "";
    }
    return "\"" + String.join("\", \"", items) + "\"";
  }
}