package org.nexus.annotations.processor;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
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
  private Types typeUtils;
  private boolean hasGenerated = false;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    filer = processingEnv.getFiler();
    elementUtils = processingEnv.getElementUtils();
    messager = processingEnv.getMessager();
    typeUtils = processingEnv.getTypeUtils();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(Mapping.class);

    if (annotatedElements.isEmpty() || hasGenerated || roundEnv.processingOver()) {
      return false;
    }

    // === PASS 1: Validate and collect routes ===
    Map<String, String> routeKeys = new HashMap<>();
    for (Element element : annotatedElements) {
      if (element.getKind() != ElementKind.METHOD) {
        messager.printMessage(Kind.ERROR, "@Mapping can only be used on methods", element);
        return false;
      }

      ExecutableElement method = (ExecutableElement) element;
      Mapping mapping = method.getAnnotation(Mapping.class);
      String endpoint = mapping.endpoint();
      String httpMethod = mapping.type().name();
      String routeKey = httpMethod + " " + endpoint;

      if (routeKeys.containsKey(routeKey)) {
        String existing = routeKeys.get(routeKey);
        messager.printMessage(Kind.ERROR,
            "Duplicate route: '%s' already defined in '%s'".formatted(routeKey, existing),
            element);
        return false;
      }
      routeKeys.put(routeKey,
          method.getEnclosingElement().getSimpleName() + "." + method.getSimpleName());

      // Validate path params vs method params
      List<String> placeholders = extractPlaceholders(endpoint);
      List<? extends VariableElement> parameters = method.getParameters();
      if (placeholders.size() != parameters.size()) {
        messager.printMessage(Kind.ERROR,
            "Path '%s' has %d placeholders but method has %d parameters"
                .formatted(endpoint, placeholders.size(), parameters.size()),
            method);
        return false;
      }
    }

    // === PASS 2: Generate code ===
    StringBuilder builder = new StringBuilder();
    builder.append("""
        package %s;
        
        import org.nexus.annotations.Route;
        import org.nexus.ProblemDetails;
        import org.nexus.enums.ProblemDetailsTypes;
        import org.nexus.exceptions.ProblemDetailsException;
        import io.netty.channel.ChannelHandlerContext;
        import io.netty.handler.codec.http.HttpRequest;
        import io.netty.handler.codec.http.HttpMethod;
        import java.util.*;
        import java.util.stream.Collectors;
        
        public final class %s {
          private static final Map<String, Route<?>> routeMap = new HashMap<>();
        
          static {
            try {
              initRoutes();
            } catch (Exception e) {
              throw new RuntimeException("Failed to initialize routes", e);
            }
          }
        
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
        
          private static void initRoutes() {
        """.formatted(GENERATED_PACKAGE, GENERATED_FILE_NAME));

    for (Element element : annotatedElements) {
      if (element.getKind() != ElementKind.METHOD) {
        continue;
      }

      ExecutableElement method = (ExecutableElement) element;
      TypeElement classElement = (TypeElement) method.getEnclosingElement();
      Mapping mapping = method.getAnnotation(Mapping.class);

      String className = classElement.getQualifiedName().toString();
      String methodName = method.getSimpleName().toString();
      String endpoint = mapping.endpoint();
      String httpMethod = "HttpMethod." + mapping.type();
      String routeKey = "\"" + mapping.type().name() + " " + endpoint + "\"";

      // === Extract Response<T> generic type ===
      String responseGenericType = getResponseGenericType(method);
      if (responseGenericType == null) {
        messager.printMessage(Kind.ERROR, "Failed to extract Response<T> type", method);
        return false;
      }

      // === Generate parameter extraction + conversion ===
      List<String> placeholders = extractPlaceholders(endpoint);
      List<? extends VariableElement> parameters = method.getParameters();

      StringBuilder paramCode = new StringBuilder();
      StringBuilder invokeArgs = new StringBuilder();

      for (int i = 0; i < parameters.size(); i++) {
        String placeholder = placeholders.get(i);
        VariableElement param = parameters.get(i);
        String paramName = param.getSimpleName().toString();
        TypeMirror paramType = param.asType();
        String typeName = paramType.toString();

        String rawValue = "params.get(\"%s\")".formatted(placeholder);

        String conversionCode;

        switch (typeName) {
          case "java.lang.String" -> conversionCode = rawValue;
          case "java.lang.Integer", "int" -> conversionCode = "safeParseInt(%s, \"%s\", \"%s\")"
              .formatted(rawValue, placeholder, endpoint);
          case "java.lang.Long", "long" -> conversionCode = "safeParseLong(%s, \"%s\", \"%s\")"
              .formatted(rawValue, placeholder, endpoint);
          default -> {
            messager.printMessage(
                Kind.ERROR,
                "Unsupported path parameter type: '%s'. Use String, Integer, int, Long, long."
                    .formatted(typeName),
                param);
            return false;
          }
        }

        // Generate variable declaration
        paramCode
            .append("        ")
            .append(typeName).append(" ").append(paramName)
            .append(" = ").append(conversionCode)
            .append(";\n");

        invokeArgs.append(paramName);
        if (i < parameters.size() - 1) {
          invokeArgs.append(", ");
        }
      }

      builder.append("""
              routeMap.put(
                %s,
                new Route<%s>(%s, "%s", (ctx, req, params) -> {
                  %s
                  %s controller = new %s();
                  return controller.%s(%s);
                }));
          """.formatted(
          routeKey,
          responseGenericType,
          httpMethod,
          endpoint,
          paramCode.toString().trim(),
          className,
          className,
          methodName,
          invokeArgs
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
        """);

    try {
      JavaFileObject sourceFile = filer.createSourceFile(GENERATED_PACKAGE_FILE);
      try (PrintWriter writer = new PrintWriter(sourceFile.openWriter())) {
        writer.write(builder.toString());
      }
      hasGenerated = true;
      messager.printMessage(Kind.NOTE, "GeneratedRoutes.java created successfully");
    } catch (Exception e) {
      messager.printMessage(Kind.ERROR, "Failed to generate file: " + e.getMessage());
      return false;
    }

    return true;
  }

  private List<String> extractPlaceholders(String endpoint) {
    List<String> names = new ArrayList<>();
    Matcher m = Pattern.compile(":([^/]+)").matcher(endpoint);
    while (m.find()) {
      names.add(m.group(1));
    }
    return names;
  }

  private String getResponseGenericType(ExecutableElement method) {
    TypeMirror returnType = method.getReturnType();
    String returnStr = returnType.toString();

    if (!returnStr.startsWith("org.nexus.Response<")) {
      return "Object";
    }

    TypeElement responseElement = elementUtils.getTypeElement("org.nexus.Response");
    if (responseElement == null) {
      return "Object";
    }

    if (!(returnType instanceof DeclaredType declared)) {
      return "Object";
    }

    List<? extends TypeMirror> typeArgs = declared.getTypeArguments();
    if (typeArgs.isEmpty()) {
      return "Object";
    }

    TypeMirror tArg = typeArgs.getFirst();
    return tArg.toString(); // e.g. "java.lang.Integer"
  }
}