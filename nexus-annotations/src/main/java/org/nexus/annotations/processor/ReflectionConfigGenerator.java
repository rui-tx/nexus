package org.nexus.annotations.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Generates GraalVM reflect-config.json for types used in @RequestBody parameters.
 */
final class ReflectionConfigGenerator {

  private final ProcessingEnvironment processingEnv;
  private final Messager messager;
  private final Filer filer;
  private final Types typeUtils;
  private final Elements elementUtils;
  private final Set<String> processedTypes = new HashSet<>();
  private final List<ReflectionEntry> entries = new ArrayList<>();
  private final ObjectMapper objectMapper;

  ReflectionConfigGenerator(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
    this.messager = processingEnv.getMessager();
    this.filer = processingEnv.getFiler();
    this.typeUtils = processingEnv.getTypeUtils();
    this.elementUtils = processingEnv.getElementUtils();
    this.objectMapper = new ObjectMapper();
    this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * Adds a type to be included in the reflection config. Recursively processes nested types.
   */
  void addType(TypeMirror type) {
    if (type == null) {
      return;
    }

    String typeName = getQualifiedName(type);
    if (typeName == null || processedTypes.contains(typeName)) {
      return;
    }

    // Skip primitive types and common Java types that don't need reflection
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

    messager.printMessage(Kind.NOTE,
        "Adding reflection config for: " + typeName);

    // Process nested types (fields of this type)
    processNestedTypes(typeElement);
  }

  /**
   * Creates a reflection entry for a type element.
   */
  private ReflectionEntry createReflectionEntry(TypeElement typeElement) {
    ReflectionEntry entry = new ReflectionEntry();
    entry.name = typeElement.getQualifiedName().toString();

    // For records and regular classes, we need:
    // - All constructors (including canonical constructor for records)
    // - All methods (getters/accessors)
    // - All fields
    entry.allDeclaredConstructors = true;
    entry.allDeclaredMethods = true;
    entry.allDeclaredFields = true;

    // Enable unsafe allocation for Jackson (helps with some edge cases)
    entry.unsafeAllocated = true;

    return entry;
  }

  /**
   * Recursively processes fields of a type to find nested types.
   */
  private void processNestedTypes(TypeElement typeElement) {
    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.FIELD) {
        TypeMirror fieldType = enclosed.asType();

        // Handle generic types (e.g., List<String>)
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

      // Handle nested classes (like Api$PostRequest)
      if (enclosed.getKind() == ElementKind.CLASS ||
          enclosed.getKind() == ElementKind.RECORD) {
        addType(enclosed.asType());
      }
    }
  }

  /**
   * Writes the reflection config JSON file.
   */
  void writeConfig() throws IOException {
    if (entries.isEmpty()) {
      messager.printMessage(Kind.NOTE,
          "No types requiring reflection config found");
      return;
    }

    // Sort entries by name for consistent output
    entries.sort(Comparator.comparing(e -> e.name));

    // Create JSON using Jackson
    String json = objectMapper.writeValueAsString(entries);

    // Write to META-INF/native-image/reflect-config.json
    try {
      FileObject resource = filer.createResource(
          StandardLocation.CLASS_OUTPUT,
          "",
          "META-INF/native-image/reflect-config.json"
      );

      try (Writer writer = resource.openWriter()) {
        writer.write(json);
      }

      messager.printMessage(Kind.NOTE,
          "Generated reflect-config.json with " + entries.size() + " entries");

    } catch (IOException e) {
      messager.printMessage(Kind.WARNING,
          "Could not write reflect-config.json: " + e.getMessage());

      // Fallback: write to root of classes directory
      FileObject fallback = filer.createResource(
          StandardLocation.CLASS_OUTPUT,
          "",
          "reflect-config.json"
      );

      try (Writer writer = fallback.openWriter()) {
        writer.write(json);
      }

      messager.printMessage(Kind.NOTE,
          "Wrote reflect-config.json to class output root instead");
    }
  }

  // Helper methods

  private String getQualifiedName(TypeMirror type) {
    if (type instanceof DeclaredType declaredType) {
      Element element = declaredType.asElement();
      if (element instanceof TypeElement) {
        return ((TypeElement) element).getQualifiedName().toString();
      }
    }
    return type.toString();
  }

  private TypeElement getTypeElement(TypeMirror type) {
    if (type instanceof DeclaredType declaredType) {
      Element element = declaredType.asElement();
      if (element instanceof TypeElement) {
        return (TypeElement) element;
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

  /**
   * JSON structure for GraalVM reflection config entries.
   */
  private static class ReflectionEntry {

    public String name;
    public Boolean allDeclaredConstructors;
    public Boolean allDeclaredMethods;
    public Boolean allDeclaredFields;
    public Boolean unsafeAllocated;
  }
}