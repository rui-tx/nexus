package org.nexus.annotations;

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
import org.nexus.security.SecurityRule;

@SupportedAnnotationTypes("org.nexus.annotations.Secured")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class SecurityProcessor extends AbstractProcessor {

  private static final String GENERATED_PACKAGE = "nexus.generated";
  private static final String CONFIG_CLASS = "SecurityConfig";

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      return false;
    }

    try {
      // Process all elements annotated with @Secured
      Set<SecurityRuleInfo> rules = new HashSet<>();
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

  private void processElement(Element element, Set<SecurityRuleInfo> rules) {
    // Get the @Secured annotation
    Secured secured = element.getAnnotation(Secured.class);
    if (secured == null) {
      return;
    }

    // Get HTTP method and path from the mapping annotation
    // This is a simplified version - you'll need to adapt it to your framework
    String httpMethod = "GET"; // Extract from @GetMapping, @PostMapping, etc.
    String path = "/";         // Extract from the mapping annotation

    // Get class and method names
    String className = ((TypeElement) element.getEnclosingElement()).getQualifiedName().toString();
    String methodName = element.getSimpleName().toString();

    // Create and add the security rule
    SecurityRuleInfo rule = new SecurityRuleInfo(
        httpMethod,
        path,
        className,
        methodName,
        secured.permitAll(),
        Set.of(secured.value()),
        Set.of(secured.permissions())
    );
    rules.add(rule);
  }

  private void generateSecurityConfig(Set<SecurityRuleInfo> rules) throws IOException {
    String className = CONFIG_CLASS;
    JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(
        GENERATED_PACKAGE + "." + className);

    try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
      // Generate package and imports
      out.println("package " + GENERATED_PACKAGE + ";");
      out.println();
      out.println("import java.util.*;");
      out.println("import org.nexus.security.SecurityRule;");
      out.println();

      out.println("public final class " + className + " {");
      out.println("    private static final Map<String, SecurityRule> RULES = new HashMap<>();");
      out.println();

      // Static initializer
      out.println("    static {");
      for (SecurityRuleInfo rule : rules) {
        out.printf("        RULES.put(\"%s\", new SecurityRule(\n",
            SecurityRule.createKey(rule.httpMethod(), rule.path()));
        out.printf("            \"%s\", \"%s\", \"%s\", \"%s\", %b,\n",
            rule.httpMethod(), rule.path(), rule.className(), rule.methodName(),
            rule.permitAll());
        out.println("            Set.of(" + toQuotedList(rule.requiredRoles()) + "),");
        out.println("            Set.of(" + toQuotedList(rule.requiredPermissions()) + ")");
        out.println("        ));");
      }
      out.println("    }");
      out.println();

      // Getter method
      out.println("    public static SecurityRule getRule(String httpMethod, String path) {");
      out.println("        return RULES.get(SecurityRule.createKey(httpMethod, path));");
      out.println("    }");
      out.println("}");
    }
  }

  private String toQuotedList(Collection<String> items) {
    if (items.isEmpty()) {
      return "";
    }
    return "\"" + String.join("\", \"", items) + "\"";
  }

  // Helper record to hold security rule information during processing
  private record SecurityRuleInfo(
      String httpMethod,
      String path,
      String className,
      String methodName,
      boolean permitAll,
      Set<String> requiredRoles,
      Set<String> requiredPermissions
  ) {

  }
}