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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.QueryParam;

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

  private static boolean isListType(TypeMirror tm, ProcessingEnvironment env) {
    if (tm.getKind() != TypeKind.DECLARED) {
      return false;
    }
    DeclaredType dt = (DeclaredType) tm;
    Element el = dt.asElement();
    if (!(el instanceof TypeElement te)) {
      return false;
    }
    return te.getQualifiedName().contentEquals("java.util.List");
  }

  private static String getListElementType(TypeMirror tm, ProcessingEnvironment env) {
    DeclaredType dt = (DeclaredType) tm;
    List<? extends TypeMirror> args = dt.getTypeArguments();
    if (args.isEmpty()) {
      return "java.lang.String"; // default
    }
    return args.getFirst().toString();
  }

  private static String escapeJavaString(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

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
      // TODO: fix this so it works with query params
/*      List<String> placeholders = extractPlaceholders(endpoint);
      List<? extends VariableElement> parameters = method.getParameters();
      if (placeholders.size() != parameters.size()) {
        messager.printMessage(Kind.ERROR,
            "Path '%s' has %d placeholders but method has %d parameters"
                .formatted(endpoint, placeholders.size(), parameters.size()),
            method);
        return false;
      }*/
    }

    // === PASS 2: Generate code ===
    StringBuilder builder = new StringBuilder();
    builder.append("""
        package %s;
        
        import java.util.concurrent.ExecutionException;
        import org.nexus.annotations.Route;
        import org.nexus.ProblemDetails;
        import org.nexus.enums.ProblemDetailsTypes;
        import org.nexus.exceptions.ProblemDetailsException;
        import org.nexus.exceptions.ProblemDetailsException;
        import io.netty.channel.ChannelHandlerContext;
        import io.netty.handler.codec.http.HttpRequest;
        import io.netty.handler.codec.http.HttpMethod;
        import java.util.*;
        import java.util.stream.Collectors;
        import java.util.concurrent.CompletableFuture;
        
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

      // Extract Response<T> generic type
//      String responseGenericType = getResponseGenericType(method);
//      if (responseGenericType == null) {
//        messager.printMessage(Kind.ERROR, "Failed to extract Response<T> type", method);
//        return false;
//      }

      ReturnInfo ri = analyzeReturnType(method, processingEnv);
      if (ri == null) {
        return false;
      }
      String responseGenericType = ri.genericType;
      boolean needsWrap = ri.needsWrap;

      // Generate parameter extraction plus conversion
      List<String> placeholders = extractPlaceholders(endpoint);
      List<? extends VariableElement> parameters = method.getParameters();

      StringBuilder paramCode = new StringBuilder();
      StringBuilder invokeArgs = new StringBuilder();

      int placeholderIndex = 0;

      for (int i = 0; i < parameters.size(); i++) {
        VariableElement param = parameters.get(i);
        String paramName = param.getSimpleName().toString();
        String typeName = param.asType().toString();

        // @QueryParam logic
        QueryParam qp = param.getAnnotation(QueryParam.class);
        if (qp != null) {
          String qpName = qp.value();
          boolean qpRequired = qp.required();
          String qpDefault = qp.defaultValue();

          // Support List<T> for query params
          boolean isList = isListType(param.asType(), processingEnv);
          String elemType = isList ? getListElementType(param.asType(), processingEnv) : null;

          if (!isList) {
            // scalar query param: String, Integer/int, Long/long
            String raw = "rc.queryParam(\"%s\")".formatted(qpName);
            String valueExpr;

            // if 'required' is present, enforce it
            if (qpRequired) {
              valueExpr = "requireQueryParam(%s, \"%s\", \"%s\")".formatted(raw, qpName, endpoint);
            } else if (!qpDefault.isEmpty()) {
              valueExpr = "(%s != null && !%s.isEmpty()) ? %s : \"%s\""
                  .formatted(raw, raw, raw, escapeJavaString(qpDefault));
            } else {
              valueExpr = raw; // maybe null
            }

            String conv;
            switch (typeName) {
              case "java.lang.String" -> conv = valueExpr;
              case "java.lang.Integer", "int" -> conv = "safeParseIntQuery(%s, \"%s\", \"%s\")"
                  .formatted(valueExpr, qpName, endpoint);
              case "java.lang.Long", "long" -> conv = "safeParseLongQuery(%s, \"%s\", \"%s\")"
                  .formatted(valueExpr, qpName, endpoint);
              default -> {
                messager.printMessage(
                    Kind.ERROR,
                    "Unsupported @QueryParam type for parameter '%s': %s. Supported: String, Integer/int, Long/long, List<String>, List<Integer>, List<Long>."
                        .formatted(paramName, typeName),
                    param);
                return false;
              }
            }

            paramCode.append("        ").append(typeName).append(" ").append(paramName)
                .append(" = ").append(conv).append(";\n");

          } else {
            // List<T> query param: read all values
            String listRaw = "rc.queryParams(\"%s\")".formatted(qpName);
            String declType = "java.util.List<" + elemType + ">";
            String conv;

            switch (elemType) {
              case "java.lang.String" -> conv = listRaw;
              case "java.lang.Integer" -> {
                // map Strings -> Integers with per-item validation
                String tmpVar = paramName + "Raw";
                paramCode
                    .append("        ")
                    .append("java.util.List<String> ").append(tmpVar).append(" = ").append(listRaw)
                    .append(";\n");
                paramCode
                    .append("        ")
                    .append("java.util.List<Integer> ").append(paramName)
                    .append(" = new java.util.ArrayList<>();\n")
                    .append("        ")
                    .append("for (String v : ").append(tmpVar).append(") {\n")
                    .append("          ")
                    .append(paramName).append(".add(\n")
                    .append("            ")
                    .append("safeParseIntQuery(v, \"").append(qpName).append("\", \"")
                    .append(endpoint).append("\")\n")
                    .append("          );\n")
                    .append("        }\n");
                // Will append invoke arg later and continue loop
                if (i < parameters.size() - 1) {
                  invokeArgs.append(paramName).append(", ");
                } else {
                  invokeArgs.append(paramName);
                }
                continue;
              }
              case "java.lang.Long" -> {
                String tmpVar = paramName + "Raw";
                paramCode
                    .append("        ")
                    .append("java.util.List<String> ").append(tmpVar).append(" = ").append(listRaw)
                    .append(";\n");
                paramCode
                    .append("        ")
                    .append("java.util.List<Long> ").append(paramName)
                    .append(" = new java.util.ArrayList<>();\n")
                    .append("        ")
                    .append("for (String v : ").append(tmpVar).append(") {\n")
                    .append("          ")
                    .append(paramName).append(".add(\n")
                    .append("            ")
                    .append("safeParseLongQuery(v, \"").append(qpName)
                    .append("\", \"").append(endpoint).append("\")\n")
                    .append("          );\n")
                    .append("        }\n");
                if (i < parameters.size() - 1) {
                  invokeArgs.append(paramName).append(", ");
                } else {
                  invokeArgs.append(paramName);
                }
                continue;
              }
              default -> {
                messager.printMessage(
                    Kind.ERROR,
                    "Unsupported @QueryParam list element type for parameter '%s': %s. Supported: List<String>, List<Integer>, List<Long>."
                        .formatted(paramName, elemType),
                    param);
                return false;
              }
            }

            // String list case falls through here
            paramCode
                .append("        ")
                .append(declType).append(" ").append(paramName).append(" = ").append(conv)
                .append(";\n");
          }

        } else {
          // PATH parameter (positional, same pattern as you had)
          if (placeholderIndex >= placeholders.size()) {
            messager.printMessage(
                Kind.ERROR,
                "Too many method parameters: no matching path placeholder for '%s'"
                    .formatted(paramName),
                param);
            return false;
          }

          String placeholder = placeholders.get(placeholderIndex++);
          String rawValue = "rc.pathParams().get(\"%s\")".formatted(placeholder);

          String conversionCode;
          switch (typeName) {
            case "java.lang.String" -> conversionCode = rawValue;
            case "java.lang.Integer", "int" -> conversionCode =
                "safeParseInt(%s, \"%s\", \"%s\")".formatted(rawValue, placeholder, endpoint);
            case "java.lang.Long", "long" -> conversionCode =
                "safeParseLong(%s, \"%s\", \"%s\")".formatted(rawValue, placeholder, endpoint);
            default -> {
              messager.printMessage(
                  Kind.ERROR,
                  "Unsupported path parameter type: '%s'. Use String, Integer/int, Long/long."
                      .formatted(typeName),
                  param);
              return false;
            }
          }

          paramCode.append("        ")
              .append(typeName).append(" ").append(paramName)
              .append(" = ").append(conversionCode).append(";\n");
        }

        // Build invocation args list
        invokeArgs.append(paramName);
        if (i < parameters.size() - 1) {
          invokeArgs.append(", ");
        }
      }

      String returnStmt;
      String invoke = "controller.%s(%s)".formatted(methodName, invokeArgs);
      if (method.getReturnType().toString().startsWith("java.util.concurrent.CompletableFuture<")) {
        // Already returns CompletableFuture, just return it
        returnStmt = "return %s;".formatted(invoke);
      } else {
        // Wrap sync response in completed future
        returnStmt = "return CompletableFuture.completedFuture(%s);".formatted(invoke);
      }

      builder.append("""
            routeMap.put(
              %s,
              new Route<%s>(%s, "%s", rc -> {
                %s
                %s controller = new %s();
                try {
                  %s
                } catch (Exception e) {
                  return CompletableFuture.failedFuture(e);
                }
              }));
          """.formatted(
          routeKey,
          responseGenericType,
          httpMethod,
          endpoint,
          paramCode.toString().trim().isEmpty() ? "" : paramCode + "\n      ",
          className,
          className,
          returnStmt
      ));
//
//      builder.append("""
//              routeMap.put(
//                %s,
//                new Route<%s>(%s, "%s", rc -> {
//                  %s
//                  %s controller = new %s();
//                  return controller.%s(%s);
//                }));
//          """.formatted(
//          routeKey,
//          responseGenericType, httpMethod, endpoint,
//          paramCode.toString().trim(),
//          className, className,
//          methodName, invokeArgs
//      ));
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

  private ReturnInfo analyzeReturnType(ExecutableElement method, ProcessingEnvironment env) {
    TypeMirror rt = method.getReturnType();
    String rtStr = rt.toString();

    // Must be CompletableFuture<Response<T>>
    if (!rtStr.startsWith("java.util.concurrent.CompletableFuture<")) {
      error(method, "Return type must be `CompletableFuture<Response<T>>`");
      return null;
    }

    // Extract T from CompletableFuture<Response<T>>
    DeclaredType cfDt = (DeclaredType) rt;
    List<? extends TypeMirror> cfArgs = cfDt.getTypeArguments();
    if (cfArgs.isEmpty() || !cfArgs.getFirst().toString().startsWith("org.nexus.Response<")) {
      error(method, "CompletableFuture must wrap `Response<T>`");
      return null;
    }

    String genericT = getFirstTypeArg((DeclaredType) cfArgs.getFirst());
    return new ReturnInfo(genericT,
        false); // No need for wrapping with CompletableFuture.supplyAsync
  }

  private String getFirstTypeArg(DeclaredType dt) {
    return dt.getTypeArguments().isEmpty() ? "java.lang.Object"
        : dt.getTypeArguments().getFirst().toString();
  }

  private void error(Element e, String msg, Object... args) {
    messager.printMessage(Kind.ERROR, String.format(msg, args), e);
  }

  private static class ReturnInfo {

    final String genericType;
    final boolean needsWrap;  // true = @Async + Response<T> → auto supplyAsync

    ReturnInfo(String genericType, boolean needsWrap) {
      this.genericType = genericType;
      this.needsWrap = needsWrap;
    }
  }

}