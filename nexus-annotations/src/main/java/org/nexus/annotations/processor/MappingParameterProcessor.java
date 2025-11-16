package org.nexus.annotations.processor;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;
import org.nexus.annotations.QueryParam;
import org.nexus.annotations.RequestBody;
import org.nexus.annotations.RequestContextParam;

final class MappingParameterProcessor {

  private final ProcessingEnvironment processingEnv;
  private final StringBuilder paramCode = new StringBuilder();
  private final List<String> parameterNames = new ArrayList<>();
  private final List<String> placeholders;
  private final String endpoint;
  private final String spacer = "  ";
  //private final ExecutableElement method;
  private int placeholderIndex = 0;

  MappingParameterProcessor(ProcessingEnvironment processingEnv,
//      ExecutableElement method,
      List<String> placeholders,
      String endpoint) {
    this.processingEnv = processingEnv;
    //this.method = method;
    this.placeholders = placeholders;
    this.endpoint = endpoint;
  }

  void processParameter(VariableElement param, int paramIndex) {
    String paramName = param.getSimpleName().toString();
    String typeName = param.asType().toString();

    // Skip RequestContext parameters as they're handled separately
    if (param.getAnnotation(RequestContextParam.class) != null) {
      return;
    }

    // Add to parameter names
    parameterNames.add(paramName);

    // Process the parameter based on its annotation
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

    paramCode.append(listType).append(" ").append(paramName)
        .append(" = new java.util.ArrayList<>();")
        .append(" java.util.List<String> ").append(tmpVar)
        .append(" = ").append(listRaw).append(";")
        .append(" for (String v : ").append(tmpVar).append(") {");

    switch (elemType) {
      case MappingProcessorConstants.TYPE_INT -> paramCode.append(paramName)
          .append(".add(safeParseIntQuery(v, \"").append(qpName)
          .append("\", \"").append(endpoint).append("\"));");
      case MappingProcessorConstants.TYPE_LONG -> paramCode.append(paramName)
          .append(".add(safeParseLongQuery(v, \"").append(qpName)
          .append("\", \"").append(endpoint).append("\"));");
      default ->  // String
          paramCode.append(paramName).append(".add(v);");
    }

    paramCode.append(" }");
  }

  private void processScalarQueryParam(QueryParam qp, String qpName,
      String paramName, String typeName) {
    String raw = "rc.getQueryParam(\"" + qpName + "\")";
    String valueExpr = buildQueryParamValueExpression(qp, qpName, raw);
    String conversion = convertQueryParamValue(typeName, valueExpr, qpName);

    paramCode.append(typeName).append(" ").append(paramName)
        .append(" = ").append(conversion).append(";");
  }

  private void processRequestBody(VariableElement param) {
    String paramType = param.asType().toString();
    String paramName = param.getSimpleName().toString();

    // For generic types, we need to use TypeReference to properly handle type erasure
    if (paramType.contains("<")) {
      // For generic types, use TypeReference
      String typeRef = "new com.fasterxml.jackson.core.type.TypeReference<" + paramType + ">() {}";
      paramCode
          .append(paramType).append(" ").append(paramName).append(";")
          .append(" try {")
          .append(paramName)
          .append(" = DF_MAPPER.readValue(rc.getBody(), ").append(typeRef).append(");");
    } else {
      // For non-generic types, we can use .class
      paramCode
          .append(paramType).append(" ").append(paramName).append(";")
          .append(" try {")
          .append(paramName)
          .append(" = DF_MAPPER.readValue(rc.getBody(), ").append(paramType).append(".class);");
    }
    
    paramCode.append(" } catch (JsonProcessingException e) {")
        .append(" throw new ProblemDetailsException(")
        .append(" new ProblemDetails.Single(")
        .append(" ProblemDetailsTypes.CLIENT_ERROR,")
        .append(" \"Invalid request\",")
        .append(" 400,")
        .append(" \"Invalid JSON request body: \" + e.getMessage(),")
        .append(" \"\",")
        .append(" Map.of()")
        .append(" )")
        .append(" );")
        .append(" }");
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
        .append(";")
//        .append("\n")
    ;
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

  public void processRequestContextParam(VariableElement param, int paramIndex) {
    String paramType = param.asType().toString();

    if (!"org.nexus.RequestContext".equals(paramType)) {
      return;
    }

    // Add to parameter names at the correct position
    parameterNames.add(paramIndex, "rc");
  }

  String getParamCode() {
    return paramCode.toString();
  }

  String getInvokeArgs() {
    return String.join(", ", parameterNames);
  }
}
