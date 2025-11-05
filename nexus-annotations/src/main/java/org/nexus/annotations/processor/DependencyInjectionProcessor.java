package org.nexus.annotations.processor;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import org.nexus.annotations.Controller;


/**
 * Processes @Controller, @Service, and @Repository annotations to generate dependency injection
 * initialization code at compile time.
 */
@SupportedAnnotationTypes({
    "org.nexus.annotations.Controller"
//    "org.nexus.annotations.Service",
//    "org.nexus.annotations.Repository"
})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public final class DependencyInjectionProcessor extends AbstractProcessor {

  private Filer filer;
  private Messager messager;
  private boolean hasGenerated = false;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.filer = processingEnv.getFiler();
    this.messager = processingEnv.getMessager();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver() || hasGenerated) {
      return false;
    }

    try {
      List<ComponentInfo> components = collectComponents(roundEnv);
      if (components.isEmpty()) {
        return false;
      }

      // Validate dependencies and detect cycles
      validateDependencies(components);

      // Generate initialization code
      generateDIInitializer(components);

      hasGenerated = true;
      return true;
    } catch (Exception e) {
      messager.printMessage(Kind.ERROR, "Failed to generate DI code: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
  }

  private List<ComponentInfo> collectComponents(RoundEnvironment roundEnv) {
    List<ComponentInfo> components = new ArrayList<>();

    // Collect all annotated classes
//    for (Element element : roundEnv.getElementsAnnotatedWith(Repository.class)) {
//      components.add(processComponent(element, ComponentType.REPOSITORY));
//    }

//    for (Element element : roundEnv.getElementsAnnotatedWith(Service.class)) {
//      components.add(processComponent(element, ComponentType.SERVICE));
//    }

    for (Element element : roundEnv.getElementsAnnotatedWith(Controller.class)) {
      components.add(processComponent(element, ComponentType.CONTROLLER));
    }

    return components;
  }

  private ComponentInfo processComponent(Element element, ComponentType type) {
    if (element.getKind() != ElementKind.CLASS) {
      messager.printMessage(Kind.ERROR,
          type.name() + " annotation can only be applied to classes", element);
      throw new IllegalStateException("Invalid annotation target");
    }

    TypeElement classElement = (TypeElement) element;
    String qualifiedName = classElement.getQualifiedName().toString();

    // Find constructor (prefer single public constructor)
    List<ExecutableElement> constructors = new ArrayList<>();
    for (Element enclosed : classElement.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
        constructors.add((ExecutableElement) enclosed);
      }
    }

    if (constructors.isEmpty()) {
      messager.printMessage(Kind.ERROR, "No constructor found", element);
      throw new IllegalStateException("No constructor");
    }

    // Use the first public constructor, or the only constructor
    ExecutableElement constructor = constructors.size() == 1
        ? constructors.get(0)
        : constructors.stream()
            .filter(c -> c.getModifiers().contains(Modifier.PUBLIC))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No public constructor found"));

    // Extract dependencies
    List<String> dependencies = new ArrayList<>();
    for (VariableElement param : constructor.getParameters()) {
      TypeMirror paramType = param.asType();
      if (paramType instanceof DeclaredType dt) {
        dependencies.add(dt.asElement().toString());
      }
    }

    return new ComponentInfo(qualifiedName, type, dependencies, constructor);
  }

  private void validateDependencies(List<ComponentInfo> components) {
    Map<String, ComponentInfo> componentMap = new HashMap<>();
    for (ComponentInfo comp : components) {
      componentMap.put(comp.qualifiedName, comp);
    }

    // Check all dependencies exist
    for (ComponentInfo comp : components) {
      for (String dep : comp.dependencies) {
        // Special cases: DataSource and other external dependencies
        if (isExternalDependency(dep)) {
          continue;
        }

        if (!componentMap.containsKey(dep)) {
          messager.printMessage(Kind.ERROR,
              "Dependency not found: " + comp.qualifiedName + " requires " + dep +
                  " but it's not annotated with @Controller, @Service, or @Repository");
        }
      }
    }

    // TODO: Add cycle detection if needed
  }

  private boolean isExternalDependency(String type) {
    // List of known external dependencies that don't need DI annotations
    return type.equals("com.zaxxer.hikari.HikariDataSource") ||
        type.equals("javax.sql.DataSource");
  }

  private void generateDIInitializer(List<ComponentInfo> components) throws Exception {
    // Sort components by dependency order (repositories -> services -> controllers)
    List<ComponentInfo> sorted = topologicalSort(components);

    StringBuilder sb = new StringBuilder();
    sb.append("package nexus.generated;\n\n");
    sb.append("import org.nexus.NexusDIRegistry;\n");

    // Import all component types
    Set<String> imports = new HashSet<>();
    for (ComponentInfo comp : sorted) {
      imports.add(comp.qualifiedName);
    }
    for (String imp : imports) {
      sb.append("import ").append(imp).append(";\n");
    }

    sb.append("\n");
    sb.append("public final class GeneratedDIInitializer {\n\n");

    sb.append("  private GeneratedDIInitializer() {}\n\n");

    sb.append("  public static void initialize() {\n");
    sb.append("    NexusDIRegistry registry = NexusDIRegistry.getInstance();\n\n");

    // Generate instantiation code in dependency order
    Map<String, String> varNames = new HashMap<>();
    int varCounter = 0;

    for (ComponentInfo comp : sorted) {
      String simpleClassName = getSimpleClassName(comp.qualifiedName);
      String varName = Character.toLowerCase(simpleClassName.charAt(0)) +
          simpleClassName.substring(1) + (varCounter++);
      varNames.put(comp.qualifiedName, varName);

      sb.append("    // Create ").append(comp.type.name().toLowerCase()).append("\n");
      sb.append("    ").append(simpleClassName).append(" ").append(varName).append(" = new ")
          .append(simpleClassName).append("(");

      // Add constructor arguments
      List<String> args = new ArrayList<>();
      for (String dep : comp.dependencies) {
        if (isExternalDependency(dep)) {
          args.add("dataSource");
        } else {
          args.add(varNames.get(dep));
        }
      }
      sb.append(String.join(", ", args));
      sb.append(");\n");

      // Register controllers in the registry
      if (comp.type == ComponentType.CONTROLLER) {
        sb.append("    registry.register(").append(simpleClassName).append(".class, ")
            .append(varName).append(");\n");
      }

      sb.append("\n");
    }

    sb.append("    registry.lock();\n");
    sb.append("  }\n");
    sb.append("}\n");

    // Write the file
    JavaFileObject sourceFile = filer.createSourceFile("nexus.generated.GeneratedDIInitializer");
    try (PrintWriter writer = new PrintWriter(sourceFile.openWriter())) {
      writer.write(sb.toString());
    }

    messager.printMessage(Kind.NOTE,
        "Generated GeneratedDIInitializer with " + components.size() + " components");
  }

  private List<ComponentInfo> topologicalSort(List<ComponentInfo> components) {
    // Simple sorting: repositories first, then services, then controllers
    List<ComponentInfo> sorted = new ArrayList<>(components);
    sorted.sort(Comparator.comparing(c -> c.type.ordinal()));
    return sorted;
  }

  private String getSimpleClassName(String qualifiedName) {
    int lastDot = qualifiedName.lastIndexOf('.');
    return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
  }

  private enum ComponentType {
    REPOSITORY, SERVICE, CONTROLLER
  }

  private record ComponentInfo(
      String qualifiedName,
      ComponentType type,
      List<String> dependencies,
      ExecutableElement constructor
  ) {

  }
}