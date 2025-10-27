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
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import org.nexus.annotations.Mapping;

@SupportedAnnotationTypes("org.nexus.annotations.Mapping")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class MappingProcessor extends AbstractProcessor {

  private static final String GENERATED_PACKAGE = "nexus.generated";
  private static final String GENERATED_FILE_NAME = "GeneratedRoutes";
  private static final String GENERATED_PACKAGE_FILE =
      GENERATED_PACKAGE + "." + GENERATED_FILE_NAME;

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
    messager.printMessage(
        Diagnostic.Kind.NOTE,
        "Processor triggered, found %s elements"
            .formatted(roundEnv.getElementsAnnotatedWith(Mapping.class).size()));

    Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(Mapping.class);

    // Only generate if we actually have annotated elements
    if (annotatedElements.isEmpty()) {
      return false;
    }

    // Skip if we've already generated or if processing is over or no annotations found
    if (hasGenerated || roundEnv.processingOver() || annotations.isEmpty()) {
      return false;
    }

    // Two passes strategy
    // 1st pass, collects all routes and check for duplicates
    //    and if annotation is annotated only on methods
    // 2nd pass, generate the routes

    // 1st pass: collect all routes to detect duplicates and if annotation is only on methods
    Map<String, String> routeKeys = new HashMap<>();
    for (Element element : annotatedElements) {
      if (element.getKind() != ElementKind.METHOD) {
        messager.printMessage(
            Kind.ERROR,
            "@Mapping annotation can only be used on methods", element);
        throw new IllegalStateException(
            "@Mapping annotation can only be used on methods. Element: %s".formatted(element));
      }

      ExecutableElement method = (ExecutableElement) element;
      Mapping mapping = method.getAnnotation(Mapping.class);
      String endpoint = mapping.endpoint();
      String httpMethod = mapping.type().name();
      String routeKey = httpMethod + " " + endpoint;

      // Check for duplicate routes
      if (routeKeys.containsKey(routeKey)) {
        String existingMethod = routeKeys.get(routeKey);
        throw new IllegalStateException(
            "Duplicate route found: '%s'. Already defined in '%s'"
                .formatted(routeKey, existingMethod));

      }
      routeKeys.put(
          routeKey,
          method.getEnclosingElement().getSimpleName() + "." + method.getSimpleName());
    }

    StringBuilder builder = new StringBuilder();
    builder.append("""
        package %s;
        
        import org.nexus.annotations.Route;
        import io.netty.channel.ChannelHandlerContext;
        import io.netty.handler.codec.http.HttpRequest;
        import io.netty.handler.codec.http.HttpMethod;
        import java.util.*;
        import java.util.stream.Collectors;
        
        public class %s {
          private static final Map<String, Route<?>> routeMap = new HashMap<>();
        
          static {
            try {
              initRoutes();
            } catch (IllegalStateException e) {
              throw new RuntimeException("Failed to initialize routes: " + e.getMessage(), e);
            }
          }
        
          private static void initRoutes() {
        """.formatted(
        GENERATED_PACKAGE,
        GENERATED_FILE_NAME
    ));

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
        responseTypeArg =
            genericPart.substring("org.nexus.Response<".length(), genericPart.length() - 1);
      }

      String paramCode = "";
      List<? extends VariableElement> parameters = method.getParameters();
      if (!parameters.isEmpty()) {
        VariableElement param = parameters.getFirst();
        String paramName = param.getSimpleName().toString();
        paramCode = "String " + paramName + " = params.get(\"" + paramName + "\");\n";
      }

      builder.append("""
              routeMap.put(
                %s,
                new Route<%s>(%s, "%s", (ctx, req, params) -> {
                  %s %s controller = new %s();
                  return controller.%s(%s);
                }));
          """.formatted(
          routeKey,
          responseTypeArg,
          httpMethod,
          endpoint,
          paramCode.trim(),
          className,
          className,
          methodName,
          parameters.isEmpty() ? "" : parameters.getFirst().getSimpleName()
      ));
    }

    builder.append("""
          }
        
          public static Route<?> getRoute(String method, String path) {
            return routeMap.get(method + " " + path);
          }
        
          public static List<Route<?>> getRoutes() {
            return new ArrayList<>(routeMap.values());
          }
        }
        """);

    try {
      JavaFileObject sourceFile = filer.createSourceFile(GENERATED_PACKAGE_FILE);
      try (PrintWriter writer = new PrintWriter(sourceFile.openWriter())) {
        writer.write(builder.toString());
      }

      hasGenerated = true;  // Mark as generated
      messager.printMessage(
          Diagnostic.Kind.NOTE,
          "GeneratedRoutes.java created successfully");
    } catch (Exception e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR,
          "Failed to generate file: " + e.getMessage());
    }

    return true;
  }
}