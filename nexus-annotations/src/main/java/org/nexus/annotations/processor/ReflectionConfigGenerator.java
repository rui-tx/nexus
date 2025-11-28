package org.nexus.annotations.processor;

import static org.nexus.NexusUtils.MAPPER_REFLECTION_CFG;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Generates GraalVM reflect-config.json for domain used in @RequestBody parameters.
 */
final class ReflectionConfigGenerator {

  private static final String FILE_NAME = "generated-reflect-config.json";
  private static final String FILE_PATH = "META-INF/native-image/%s".formatted(FILE_NAME);

  private final Messager messager;
  private final Filer filer;
  private final Elements elementUtils;
  private final Set<String> processedTypes = new HashSet<>();
  private final List<ReflectionEntry> entries = new ArrayList<>();

  ReflectionConfigGenerator(ProcessingEnvironment processingEnv) {
    this.messager = processingEnv.getMessager();
    this.filer = processingEnv.getFiler();
    this.elementUtils = processingEnv.getElementUtils();
  }

  /**
   * Adds a type to be included in the reflection config
   */
  void addType(TypeMirror type) {
    if (type == null) {
      return;
    }

    String typeName = getQualifiedName(type);
    if (typeName == null || processedTypes.contains(typeName)) {
      return;
    }

    // Skip primitive domain and common Java domain that don't need reflection
    if (isPrimitiveOrBoxed(typeName) || isCommonJavaType(typeName)) {
      return;
    }

    processedTypes.add(typeName);

    TypeElement typeElement = getTypeElement(type);
    if (typeElement == null) {
      return;
    }

    // Add the main type
    ReflectionEntry entry = createReflectionEntry(typeElement);
    entries.add(entry);

    messager.printMessage(Kind.WARNING,
        "Adding reflection config for: %s".formatted(typeName));

    // Process nested domain (fields of this type)
    processNestedTypes(typeElement);
  }

  /**
   * Creates a reflection entry for a type element.
   */
  private ReflectionEntry createReflectionEntry(TypeElement typeElement) {
    return new ReflectionEntry(
        elementUtils.getBinaryName(typeElement).toString(),
        true,
        true,
        true,
        true,
        true
    );
  }

  /**
   * Recursively processes fields of a type to find nested domain.
   */
  private void processNestedTypes(TypeElement typeElement) {
    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.FIELD) {
        TypeMirror fieldType = enclosed.asType();

        // Handle generic domain (e.g., List<String>)
        if (fieldType instanceof DeclaredType declaredType) {

          // Process the container type (e.g., List)
          addType(declaredType);

          // Process generic type arguments (e.g., String in List<String>)
          for (TypeMirror typeArg : declaredType.getTypeArguments()) {
            addType(typeArg);
          }
        } else {
          addType(fieldType);
        }
      }

      // Handle nested classes, like Api$ExampleRequest
      if (enclosed.getKind() == ElementKind.CLASS ||
          enclosed.getKind() == ElementKind.RECORD) {
        addType(enclosed.asType());
      }
    }
  }

  public void processReturnType(TypeMirror returnType) {
    if (returnType == null) {
      return;
    }

    // Handle CompletableFuture<Response<T>> pattern
    if (returnType.getKind() == TypeKind.DECLARED) {
      DeclaredType declaredType = (DeclaredType) returnType;
      String typeName = declaredType.toString();

      // If it's a CompletableFuture with Response<T>, process its type arguments
      if (typeName.startsWith("java.util.concurrent.CompletableFuture<")) {
        if (!declaredType.getTypeArguments().isEmpty()) {
          TypeMirror futureTypeArg = declaredType.getTypeArguments().getFirst();
          if (futureTypeArg.toString().startsWith("org.nexus.Response<")) {
            if ((futureTypeArg instanceof DeclaredType responseType)
                && !responseType.getTypeArguments().isEmpty()) {
              TypeMirror responseTypeArg = responseType.getTypeArguments().getFirst();
              messager.printMessage(Kind.NOTE,
                  "Processing response type: " + responseTypeArg);
              processTypeRecursively(responseTypeArg);
            }
          } else {
            // Process the type argument directly if it's not a Response
            processTypeRecursively(futureTypeArg);
          }
        }
      } else {
        // Process non-Future return domain directly
        processTypeRecursively(returnType);
      }
    } else {
      // Process non-declared domain (primitives, arrays, etc.)
      processTypeRecursively(returnType);
    }
  }


  private void processTypeRecursively(TypeMirror type) {
    if (type == null) {
      messager.printMessage(Kind.ERROR, "Type is null");
      return;
    }

    messager.printMessage(Kind.NOTE, "Processing type: " + type +
        " (kind: " + type.getKind() + ")");

    if (type.getKind() == TypeKind.ARRAY) {
      TypeMirror componentType = ((javax.lang.model.type.ArrayType) type).getComponentType();
      processTypeRecursively(componentType);
      return;
    }

    // Only process declared domain (classes, interfaces)
    if (type.getKind() != TypeKind.DECLARED) {
      return;
    }

    DeclaredType declaredType = (DeclaredType) type;
    Element element = declaredType.asElement();

    if (!(element instanceof TypeElement typeElement)) {
      return;
    }

    String typeName = typeElement.getQualifiedName().toString();
    String binaryName = elementUtils.getBinaryName(typeElement).toString();

    messager.printMessage(Kind.NOTE, "Type: " + typeName +
        ", Binary: " + binaryName +
        ", Kind: " + typeElement.getKind());

    // Skip already processed domain
    if (processedTypes.contains(binaryName)) {
      return;
    }

    // Skip primitives and common Java domain (but still process their type arguments)
    boolean skipAdding = isPrimitiveOrBoxed(typeName) || isCommonJavaType(typeName);
    if (!skipAdding) {
      messager.printMessage(Kind.NOTE, "Adding type to reflection config: " + binaryName);

      // Create and add the reflection entry
      ReflectionEntry entry = createReflectionEntry(typeElement);
      entries.add(entry);
      processedTypes.add(binaryName);

      // Process nested domain (fields, etc.)
      processNestedTypes(typeElement);
    }

    // ALWAYS process type arguments, even for common domain like List
    for (TypeMirror typeArg : declaredType.getTypeArguments()) {
      // Special handling for type arguments in nested classes
      if (typeArg.toString().contains(".") && !typeArg.toString().startsWith("java.")) {
        processNestedType(typeArg.toString());
      }
      processTypeRecursively(typeArg);
    }

    // Special handling for nested classes in the current compilation unit
    if (typeName.contains(".") && !typeName.startsWith("java.")) {
      processNestedType(typeName);
    }
  }

  private void processNestedType(String typeName) {
    // Convert "org.nexus.Example.Foo" to "org.nexus.Example$Foo"
    String binaryName = typeName.replace('.', '$');

    // If it's a nested class (contains $ but not from an inner class)
    if (typeName.contains(".") && !binaryName.contains("$")) {
      binaryName = typeName.replace('.', '$');
    }

    // Skip if already processed or is a common Java type
    if (processedTypes.contains(binaryName) ||
        isPrimitiveOrBoxed(binaryName) ||
        isCommonJavaType(binaryName)) {
      return;
    }

    try {
      // Try to get the type element
      TypeElement nestedType = elementUtils.getTypeElement(typeName);
      if (nestedType != null) {
        String nestedBinaryName = elementUtils.getBinaryName(nestedType).toString();
        if (!processedTypes.contains(nestedBinaryName)) {
          messager.printMessage(Kind.NOTE, "Adding nested type: " + nestedBinaryName);
          ReflectionEntry entry = createReflectionEntry(nestedType);
          entries.add(entry);
          processedTypes.add(nestedBinaryName);
          processNestedTypes(nestedType);
        }
      } else {
        // If we can't find it directly, try with the binary name
        nestedType = elementUtils.getTypeElement(binaryName);
        if (nestedType != null && !processedTypes.contains(binaryName)) {
          messager.printMessage(Kind.NOTE, "Adding nested type (binary): " + binaryName);
          ReflectionEntry entry = createReflectionEntry(nestedType);
          entries.add(entry);
          processedTypes.add(binaryName);
          processNestedTypes(nestedType);
        }
      }
    } catch (Exception e) {
      messager.printMessage(Kind.ERROR,
          "Error processing nested type " + typeName + ": " + e.getMessage());
    }
  }

  /**
   * Writes the reflection config JSON file.
   */
  void writeConfig() throws IOException {
    if (entries.isEmpty()) {
      messager.printMessage(Kind.NOTE,
          "No domain requiring reflection config found");

      FileObject resource = filer.createResource(
          StandardLocation.CLASS_OUTPUT,
          "",
          FILE_PATH
      );

      try (Writer writer = resource.openWriter()) {
        writer.write("[]");
      }

      return;
    }

    // Sort entries by name for consistent output
    entries.sort(Comparator.comparing(e -> e.name));

    String json = MAPPER_REFLECTION_CFG.writeValueAsString(entries);

    try {
      FileObject resource = filer.createResource(
          StandardLocation.CLASS_OUTPUT,
          "",
          FILE_PATH
      );

      try (Writer writer = resource.openWriter()) {
        writer.write(json);
      }

      messager.printMessage(Kind.NOTE,
          "Generated %s with %d entries".formatted(FILE_NAME, entries.size()));

    } catch (IOException e) {
      messager.printMessage(Kind.WARNING,
          "Could not write %s: %s".formatted(FILE_NAME, e.getMessage()));

      // Fallback: write to root of classes directory
      FileObject fallback = filer.createResource(
          StandardLocation.CLASS_OUTPUT,
          "",
          FILE_NAME
      );

      try (Writer writer = fallback.openWriter()) {
        writer.write(json);
      }

      messager.printMessage(Kind.NOTE,
          "Wrote %s to class output root instead".formatted(FILE_NAME));
    }
  }

  private String getQualifiedName(TypeMirror type) {
    if (type instanceof DeclaredType declaredType) {
      Element element = declaredType.asElement();
      if (element instanceof TypeElement typeelement) {
        return typeelement.getQualifiedName().toString();
      }
    }
    return type.toString();
  }

  private TypeElement getTypeElement(TypeMirror type) {
    if (type instanceof DeclaredType declaredType) {
      Element element = declaredType.asElement();
      if (element instanceof TypeElement typeelement) {
        return typeelement;
      }
    }
    return null;
  }

  private boolean isPrimitiveOrBoxed(String typeName) {
    return typeName.equals("byte") || typeName.equals("short") ||
        typeName.equals("int") || typeName.equals("long") ||
        typeName.equals("float") || typeName.equals("double") ||
        typeName.equals("boolean") || typeName.equals("char") ||
        typeName.equals("java.lang.Byte") ||
        typeName.equals("java.lang.Short") ||
        typeName.equals("java.lang.Integer") ||
        typeName.equals("java.lang.Long") ||
        typeName.equals("java.lang.Float") ||
        typeName.equals("java.lang.Double") ||
        typeName.equals("java.lang.Boolean") ||
        typeName.equals("java.lang.Character");
  }

  private boolean isCommonJavaType(String typeName) {
    return typeName.equals("java.lang.String") ||
        typeName.equals("java.lang.Object") ||
        typeName.startsWith("java.util.List") ||
        typeName.startsWith("java.util.Map") ||
        typeName.startsWith("java.util.Set") ||
        typeName.startsWith("java.util.Collection");
  }

  private record ReflectionEntry(
      String name,
      Boolean allDeclaredConstructors,
      Boolean allDeclaredMethods,
      Boolean allDeclaredFields,
      Boolean queryAllDeclaredConstructors,
      Boolean queryAllPublicConstructors) {

  }
}