package org.nexus.annotations.processor;

import java.io.PrintWriter;
import java.util.ArrayList;
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
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.RequestBody;
import org.nexus.annotations.RequestContextParam;

@SupportedAnnotationTypes("org.nexus.annotations.Mapping")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public final class MappingProcessor extends AbstractProcessor {

  private final String spacer = "  ";

  private Filer filer;
  private Messager messager;
  private boolean hasGenerated = false;
  private ReflectionConfigGenerator reflectionConfigGenerator;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.filer = processingEnv.getFiler();
    this.messager = processingEnv.getMessager();
    this.reflectionConfigGenerator = new ReflectionConfigGenerator(processingEnv);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver() || hasGenerated) {
      return false;
    }

    Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(Mapping.class);
    if (annotatedElements.isEmpty()) {
      return false;
    }

    try {
      validateAndGenerateRoutes(annotatedElements);
      hasGenerated = true;
      return true;
    } catch (Exception e) {
      messager.printMessage(Kind.ERROR, "Failed to generate routes: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
  }

  private void validateAndGenerateRoutes(Set<? extends Element> elements) throws Exception {
    Map<String, String> routeKeys = new HashMap<>();
    List<RouteInfo> routes = new ArrayList<>();

    // First pass: validate all methods and collect route information plus reflection types
    for (Element element : elements) {
      if (element.getKind() != ElementKind.METHOD) {
        messager.printMessage(
            Kind.ERROR,
            MappingProcessorConstants.ERROR_MAPPING_ONLY_ON_METHODS,
            element);
        return;
      }

      ExecutableElement method = (ExecutableElement) element;
      Mapping mapping = method.getAnnotation(Mapping.class);
      String routeKey = mapping.type().name() + " " + mapping.endpoint();

      // Check for duplicate routes
      if (routeKeys.containsKey(routeKey)) {
        messager.printMessage(Kind.ERROR,
            String.format(
                MappingProcessorConstants.ERROR_DUPLICATE_ROUTE,
                routeKey,
                routeKeys.get(routeKey)),
            element);
        return;
      }
      routeKeys.put(routeKey,
          method.getEnclosingElement().getSimpleName() + "." + method.getSimpleName());

      // Validate return type
      MappingProcessorUtils.validateMethodReturnType(method, messager);

      // Collect @RequestBody types for reflection config
      collectReflectionTypes(method);

      routes.add(new RouteInfo(method, mapping));
    }

    // Second pass: generate code
    generateRoutesFile(routes);

    // Third pass: generate reflection config
    reflectionConfigGenerator.writeConfig();
  }

  /**
   * Collects all types that need reflection configuration.
   */
  private void collectReflectionTypes(ExecutableElement method) {
    // Process method parameters with @RequestBody
    for (VariableElement param : method.getParameters()) {
      RequestBody requestBody = param.getAnnotation(RequestBody.class);
      if (requestBody != null) {
        // Add the parameter type to reflection config
        reflectionConfigGenerator.addType(param.asType());

        messager.printMessage(Kind.NOTE,
            "Found @RequestBody parameter: " + param.asType() +
                " in method " + method.getSimpleName());
      }
    }

    // Process return type
    TypeMirror returnType = method.getReturnType();
    reflectionConfigGenerator.processReturnType(returnType);

    messager.printMessage(Kind.NOTE,
        "Processing return type: " + returnType +
            " for method " + method.getSimpleName());
  }

  private void generateRoutesFile(List<RouteInfo> routes) throws Exception {
    StringBuilder builder = new StringBuilder();
    builder.append(MappingProcessorConstants.GENERATED_CLASS_HEADER)
        .append(MappingProcessorConstants.HELPER_METHODS)
        .append("  private static void initRoutes() {\n");

    // Add route mappings
    for (RouteInfo route : routes) {
      builder.append(spacer.repeat(2)).append(generateRouteMapping(route)).append("\n");
    }

    builder.append("  }")
        .append(MappingProcessorConstants.GENERATED_CLASS_FOOTER);

    writeGeneratedFile(builder.toString());
  }

  private String generateRouteMapping(RouteInfo route) {
    ExecutableElement method = route.method;
    String className = ((TypeElement) method.getEnclosingElement()).getQualifiedName().toString();
    String methodName = method.getSimpleName().toString();
    String endpoint = route.mapping.endpoint();
    String httpMethod = "HttpMethod." + route.mapping.type().name();
    String methodStr = route.mapping.type().name();

    List<String> placeholders = MappingProcessorUtils.extractPlaceholders(endpoint);
    MappingParameterProcessor paramProcessor = new MappingParameterProcessor(
        processingEnv, placeholders, endpoint); //method

    List<? extends VariableElement> parameters = method.getParameters();
    for (int i = 0; i < parameters.size(); i++) {
      if (parameters.get(i).getAnnotation(RequestContextParam.class) != null) {
        paramProcessor.processRequestContextParam(parameters.get(i), i);
        continue;
      }

      paramProcessor.processParameter(parameters.get(i), i);
    }

    String paramCode = paramProcessor.getParamCode();
    String invokeArgs = paramProcessor.getInvokeArgs();
    String responseType = MappingProcessorUtils.getResponseGenericType(method);

    boolean isExact = placeholders.isEmpty();

    StringBuilder sb1 = new StringBuilder();
    sb1.append("new Route<%s>(%s, \"%s\", rc -> {\n")
        .append(spacer.repeat(7))
        .append(paramCode.isEmpty() ? "" : paramCode)
        .append("%s  %s controller = org.nexus.NexusBeanScope.get().get(%s.class);\n")
        .append(spacer.repeat(7))
        .append("try {\n")
        .append(spacer.repeat(8))
        .append("return controller.%s(%s);\n")
        .append(spacer.repeat(7))
        .append("} catch (Exception e) {\n")
        .append(spacer.repeat(8))
        .append("return CompletableFuture.failedFuture(e); }})");

    ;

    String routeCreation = String.format(
        sb1.toString(),
        responseType, httpMethod, endpoint,
        "", className,  // paramCode (handled above), className
        className, methodName, invokeArgs
    );

    if (isExact) {
      // Exact route: Use normalized key for put
      return String.format(
          "exactRoutes.put(\"%s \" + PathMatcher.normalise(\"%s\"), %s);\n",
          methodStr, endpoint, routeCreation
      );
    } else {
      // Dynamic route: Precompile and add to list

      StringBuilder sb2 = new StringBuilder();
      sb2.append("dynamicRoutesByMethod.computeIfAbsent(\"%s\", k -> new ArrayList<>())")
          .append("\n")
          .append(spacer.repeat(4))
          .append(".add(new CompiledRoute(CompiledPattern.compile(\"%s\"),")
          .append("\n")
          .append(spacer.repeat(6))
          .append("%s));\n")
      ;

      return String.format(sb2.toString(), methodStr, endpoint, routeCreation
      );
    }
  }

  private void writeGeneratedFile(String content) throws Exception {
    JavaFileObject sourceFile = filer.createSourceFile(
        MappingProcessorConstants.GENERATED_PACKAGE_FILE);
    try (PrintWriter writer = new PrintWriter(sourceFile.openWriter())) {
      writer.write(content);
    }
    messager.printMessage(
        Kind.NOTE,
        MappingProcessorConstants.GENERATED_FILE_NAME + ".java created successfully");
  }

  private record RouteInfo(ExecutableElement method, Mapping mapping) {

  }
}