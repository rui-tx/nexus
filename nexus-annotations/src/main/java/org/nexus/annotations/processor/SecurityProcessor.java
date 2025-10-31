package org.nexus.annotations.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.nexus.annotations.Secured;

@SupportedAnnotationTypes("org.nexus.annotations.Secured")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class SecurityProcessor extends AbstractProcessor {

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

    // Get class and method names
    String className = ((TypeElement) element.getEnclosingElement()).getQualifiedName().toString();
    String methodName = element.getSimpleName().toString();

    // Create and add the security rule
    SecurityRule rule = new SecurityRule(
        className,
        methodName,
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
      String generatedCode = """
          package %s;
          
          import java.util.*;
          import org.nexus.SecurityRule;
          
          public final class %s {
            private static final Map<String, SecurityRule> RULES = new HashMap<>();
          
            static {
          %s
            }
          
              public static SecurityRule getRule(String httpMethod, String path) {
                  return RULES.get(SecurityRule.createKey(httpMethod, path));
              }
          }
          """.formatted(
          GENERATED_PACKAGE,
          className,
          rules.stream()
              .map(rule -> """
                       RULES.put(
                         "%s",
                         new SecurityRule(
                           "%s",
                           "%s",
                           %b,
                           Set.of(%s),
                           Set.of(%s)
                         ));
                  """.formatted(
                  SecurityRule.createKey(rule.className(), rule.methodName()),
                  rule.className(),
                  rule.methodName(),
                  rule.permitAll(),
                  toQuotedList(rule.requiredRoles()),
                  toQuotedList(rule.requiredPermissions())
              ))
              .collect(Collectors.joining())
      );

      out.print(generatedCode);
    }
  }

  private String toQuotedList(Collection<String> items) {
    if (items.isEmpty()) {
      return "";
    }
    return "\"" + String.join("\", \"", items) + "\"";
  }
}