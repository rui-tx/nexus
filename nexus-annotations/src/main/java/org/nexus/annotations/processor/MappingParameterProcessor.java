package org.nexus.annotations.processor;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;
import org.nexus.annotations.QueryParam;
import org.nexus.annotations.RequestBody;

final class MappingParameterProcessor {

  private final ProcessingEnvironment processingEnv;
  private final StringBuilder paramCode = new StringBuilder();
  private final List<String> parameterNames = new ArrayList<>();
  private final StringBuilder invokeArgs = new StringBuilder();
  private final List<String> placeholders;
  private final String endpoint;
  private final ExecutableElement method;
  private int placeholderIndex = 0;

  MappingParameterProcessor(ProcessingEnvironment processingEnv,
      ExecutableElement method,
      List<String> placeholders,
      String endpoint) {
    this.processingEnv = processingEnv;
    this.method = method;
    this.placeholders = placeholders;
    this.endpoint = endpoint;
  }

  void processParameter(VariableElement param, int paramIndex) {
    String paramName = param.getSimpleName().toString();
    String typeName = param.asType().toString();

    if (param.getAnnotation(RequestBody.class) != null) {
      processRequestBody(param);
    } else {
      QueryParam qp = param.getAnnotation(QueryParam.class);
      if (qp != null) {
        processQueryParam(param, qp, paramName, typeName);
      } else {
        processPathParam(param, paramName, typeName);
      }
    }

    parameterNames.add(paramName);
  }

  private void processQueryParam(VariableElement param, QueryParam qp,
      String paramName, String typeName) {
    String qpName = qp.value();
    boolean isList = MappingProcessorUtils.isListType(param.asType(), processingEnv.getTypeUtils());

    if (isList) {
      processListQueryParam(param, qpName, paramName, typeName);
    } else {
      processScalarQueryParam(qp, qpName, paramName, typeName);
    }
  }

  private void processListQueryParam(VariableElement param, String qpName,
      String paramName, String typeName) {
    String elemType = MappingProcessorUtils.getListElementType(param.asType());
    String listType = "java.util.List<" + elemType + ">";
    String listRaw = "rc.getQueryParams(\"" + qpName + "\")";
    String tmpVar = paramName + "Raw";

    paramCode.append("        ").append(listType).append(" ").append(paramName)
        .append(" = new java.util.ArrayList<>();\n")
        .append("        java.util.List<String> ").append(tmpVar)
        .append(" = ").append(listRaw).append(";\n")
        .append("        for (String v : ").append(tmpVar).append(") {\n");

    switch (elemType) {
      case MappingProcessorConstants.TYPE_INT -> paramCode.append("          ").append(paramName)
          .append(".add(safeParseIntQuery(v, \"").append(qpName)
          .append("\", \"").append(endpoint).append("\"));\n");
      case MappingProcessorConstants.TYPE_LONG -> paramCode.append("          ").append(paramName)
          .append(".add(safeParseLongQuery(v, \"").append(qpName)
          .append("\", \"").append(endpoint).append("\"));\n");
      default ->  // String
          paramCode.append("          ").append(paramName).append(".add(v);\n");
    }

    paramCode.append("        }\n");
  }

  private void processScalarQueryParam(QueryParam qp, String qpName,
      String paramName, String typeName) {
    String raw = "rc.getQueryParam(\"" + qpName + "\")";
    String valueExpr = buildQueryParamValueExpression(qp, qpName, raw);
    String conversion = convertQueryParamValue(typeName, valueExpr, qpName);

    paramCode.append("        ").append(typeName).append(" ").append(paramName)
        .append(" = ").append(conversion).append(";\n");
  }

  private void processRequestBody(VariableElement param) {
    String paramType = param.asType().toString();
    String paramName = param.getSimpleName().toString();

    paramCode
        .append(paramType).append(" ").append(paramName).append(";\n")
        .append("        try {\n");
    paramCode.append("          ").append(paramName)
        .append(" = MAPPER.readValue(rc.getBody(), ").append(paramType).append(".class);\n");
    paramCode.append("        } catch (JsonProcessingException e) {\n");
    paramCode.append("          throw new RuntimeException(e);\n");
    paramCode.append("        }\n");

  }

  private String buildQueryParamValueExpression(QueryParam qp, String qpName, String raw) {
    if (qp.required()) {
      return "requireQueryParam(" + raw + ", \"" + qpName + "\", \"" + endpoint + "\")";
    } else if (!qp.defaultValue().isEmpty()) {
      return "(" + raw + " != null && !" + raw + ".isEmpty()) ? " + raw + " : \""
          + MappingProcessorUtils.escapeJavaString(qp.defaultValue()) + "\"";
    }
    return raw;
  }

  private String convertQueryParamValue(String typeName, String valueExpr, String paramName) {
    return switch (typeName) {
      case MappingProcessorConstants.TYPE_STRING -> valueExpr;
      case "int", MappingProcessorConstants.TYPE_INT ->
          "safeParseIntQuery(" + valueExpr + ", \"" + paramName + "\", \"" + endpoint + "\")";
      case "long", MappingProcessorConstants.TYPE_LONG ->
          "safeParseLongQuery(" + valueExpr + ", \"" + paramName + "\", \"" + endpoint + "\")";
      default ->
          throw new IllegalArgumentException("Unsupported query parameter type: " + typeName);
    };
  }

  private void processPathParam(VariableElement param, String paramName, String typeName) {
    if (placeholderIndex >= placeholders.size()) {
      processingEnv.getMessager().printMessage(Kind.ERROR,
          "Too many method parameters: no matching path placeholder for '" + paramName + "'",
          param);
      return;
    }

    String placeholder = placeholders.get(placeholderIndex++);
    String rawValue = "rc.getPathParams().get(\"" + placeholder + "\")";
    String conversion = convertPathParamValue(typeName, rawValue, placeholder);

    paramCode
        .append(typeName).append(" ").append(paramName).append(" = ").append(conversion)
        .append(";\n");
  }

  private String convertPathParamValue(String typeName, String rawValue, String placeholder) {
    return switch (typeName) {
      case MappingProcessorConstants.TYPE_STRING -> rawValue;
      case "int", MappingProcessorConstants.TYPE_INT ->
          "safeParseInt(" + rawValue + ", \"" + placeholder + "\", \"" + endpoint + "\")";
      case "long", MappingProcessorConstants.TYPE_LONG ->
          "safeParseLong(" + rawValue + ", \"" + placeholder + "\", \"" + endpoint + "\")";
      default -> throw new IllegalArgumentException("Unsupported path parameter type: " + typeName);
    };
  }

  String getParamCode() {
    return paramCode.toString();
  }

  String getInvokeArgs() {
    return String.join(", ", parameterNames);
  }
}
