package org.nexus.annotations.processor;

import static org.nexus.annotations.processor.MappingProcessorConstants.OBJECT;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

final class MappingProcessorUtils {

  private static final Pattern PATH_PARAM_PATTERN = Pattern.compile(":([^/]+)");

  private MappingProcessorUtils() {
  }

  static List<String> extractPlaceholders(String endpoint) {
    List<String> names = new java.util.ArrayList<>();
    Matcher m = PATH_PARAM_PATTERN.matcher(endpoint);
    while (m.find()) {
      names.add(m.group(1));
    }
    return names;
  }

  static boolean isListType(TypeMirror tm) {
    if (tm.getKind() != TypeKind.DECLARED) {
      return false;
    }
    DeclaredType dt = (DeclaredType) tm;
    Element el = dt.asElement();
    return el.toString().equals(MappingProcessorConstants.LIST_TYPE);
  }

  static String getListElementType(TypeMirror tm) {
    if (!(tm instanceof DeclaredType dt)) {
      return MappingProcessorConstants.TYPE_STRING;
    }
    List<? extends TypeMirror> args = dt.getTypeArguments();
    return args.isEmpty() ? MappingProcessorConstants.TYPE_STRING : args.getFirst().toString();
  }

  static String escapeJavaString(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  static void validateMethodReturnType(ExecutableElement method, Messager messager) {
    TypeMirror returnType = method.getReturnType();
    String returnStr = returnType.toString();

    if (!returnStr.startsWith(
        MappingProcessorConstants.COMPLETABLE_FUTURE + "<" +
            MappingProcessorConstants.RESPONSE_TYPE + "<")) {
      messager.printMessage(
          Kind.ERROR,
          "Return type must be `CompletableFuture<Response<T>>`", method);
    }
  }

  static String getResponseGenericType(ExecutableElement method) {
    TypeMirror returnType = method.getReturnType();
    if (!(returnType instanceof DeclaredType declaredType)) {
      return OBJECT;
    }

    List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
    if (typeArgs.isEmpty() || !(typeArgs.getFirst() instanceof DeclaredType responseType)) {
      return OBJECT;
    }

    List<? extends TypeMirror> responseArgs = responseType.getTypeArguments();
    return responseArgs.isEmpty() ? OBJECT : responseArgs.getFirst().toString();
  }
}
