package org.nexus.annotations.processor;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.nexus.annotations.Mapping;

@SupportedAnnotationTypes("org.nexus.annotations.Mapping")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class MappingProcessor extends AbstractProcessor {

  private Filer filer;
  private Elements elementUtils;
  private Messager messager;
  private boolean hasGenerated = false;  // Track if we've generated the file

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    filer = processingEnv.getFiler();
    elementUtils = processingEnv.getElementUtils();
    messager = processingEnv.getMessager();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    messager.printMessage(Diagnostic.Kind.NOTE, "Processor triggered, found " +
        roundEnv.getElementsAnnotatedWith(Mapping.class).size() + " elements");

    // Skip if we've already generated or if processing is over or no annotations found
    if (hasGenerated || roundEnv.processingOver() || annotations.isEmpty()) {
      return false;
    }

    Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(Mapping.class);

    // Only generate if we actually have annotated elements
    if (annotatedElements.isEmpty()) {
      return false;
    }

    StringBuilder builder = new StringBuilder();
    builder.append("package nexus.generated;\n\n")
        .append("import org.nexus.annotations.Route;\n")
        .append("import io.netty.channel.ChannelHandlerContext;\n")
        .append("import io.netty.handler.codec.http.HttpRequest;\n")
        .append("import io.netty.handler.codec.http.HttpMethod;\n")
        .append("import java.util.*;\n")
        .append("import java.util.stream.Collectors;\n\n")
        .append("public class GeneratedRoutes {\n")
        .append("    private static final Map<String, Route<?>> routeMap = new HashMap<>();\n\n")
        .append("    static {\n")
        .append("        try {\n")
        .append("            initRoutes();\n")
        .append("        } catch (IllegalStateException e) {\n")
        .append(
            "            throw new RuntimeException(\"Failed to initialize routes: \" + e.getMessage(), e);\n")
        .append("        }\n")
        .append("    }\n\n")
        .append("    private static void initRoutes() {\n");

    // First pass: collect all routes to detect duplicates
    Map<String, String> routeKeys = new HashMap<>();
    for (Element element : annotatedElements) {
      if (element.getKind() != ElementKind.METHOD) {
        messager.printMessage(Diagnostic.Kind.WARNING,
            "@Mapping annotation can only be used on methods", element);
        continue;
      }

      ExecutableElement method = (ExecutableElement) element;
      Mapping mapping = method.getAnnotation(Mapping.class);
      String endpoint = mapping.endpoint();
      String httpMethod = mapping.type().name();
      String routeKey = httpMethod + " " + endpoint;

      // Check for duplicate routes
      if (routeKeys.containsKey(routeKey)) {
        String existingMethod = routeKeys.get(routeKey);
        throw new IllegalStateException("Duplicate route found: " + routeKey +
            ". Already defined in " + existingMethod);
      }
      routeKeys.put(routeKey,
          method.getEnclosingElement().getSimpleName() + "." + method.getSimpleName());
    }

    // Second pass: generate the route map
    for (Element element : annotatedElements) {
      if (element.getKind() != ElementKind.METHOD) {
        continue;
      }

      ExecutableElement method = (ExecutableElement) element;
      Mapping mapping = method.getAnnotation(Mapping.class);
      TypeElement classElement = (TypeElement) method.getEnclosingElement();
      String className = classElement.getQualifiedName().toString();
      String methodName = method.getSimpleName().toString();
      String endpoint = mapping.endpoint();
      String httpMethod = "HttpMethod." + mapping.type();
      String routeKey = "\"" + mapping.type().name() + " " + endpoint + "\"";

      TypeMirror returnType = method.getReturnType();
      String responseTypeArg = "Object";

      if (returnType.toString().startsWith("org.nexus.Response<")) {
        String genericPart = returnType.toString();
        responseTypeArg = genericPart.substring("org.nexus.Response<".length(),
            genericPart.length() - 1);
      }

      String paramCode = "";
      List<? extends VariableElement> parameters = method.getParameters();
      if (!parameters.isEmpty()) {
        VariableElement param = parameters.get(0);
        String paramName = param.getSimpleName().toString();
        paramCode = "            String " + paramName + " = params.get(\"" + paramName + "\");\n";
      }

      builder.append("        routeMap.put(").append(routeKey).append(", ")
          .append("new Route<")
          .append(responseTypeArg).append(">(")
          .append(httpMethod).append(", \"")
          .append(endpoint).append("\", (ctx, req, params) -> {\n")
          .append(paramCode)
          .append("            ").append(className).append(" controller = new ")
          .append(className).append("();\n")
          .append("            return controller.").append(methodName)
          .append("(").append(parameters.isEmpty() ? "" : parameters.getFirst().getSimpleName())
          .append(");\n")
          .append("        }));\n");
    }

    builder.append("    }\n\n")
        .append("    public static Route<?> getRoute(String method, String path) {\n")
        .append("        return routeMap.get(method + \" \" + path);\n")
        .append("    }\n\n")
        .append("    public static List<Route<?>> getRoutes() {\n")
        .append("        return new ArrayList<>(routeMap.values());\n")
        .append("    }\n")
        .append("}\n");

    try {
      JavaFileObject sourceFile = filer.createSourceFile("nexus.generated.GeneratedRoutes");
      try (PrintWriter writer = new PrintWriter(sourceFile.openWriter())) {
        writer.write(builder.toString());
      }
      hasGenerated = true;  // Mark as generated
      messager.printMessage(Diagnostic.Kind.NOTE, "GeneratedRoutes.java created successfully");
    } catch (Exception e) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate file: " + e.getMessage());
      e.printStackTrace();
    }

    return true;
  }
}