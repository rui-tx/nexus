package org.nexus.enums;

public final class ProblemDetailsTypes {

  public static final String SERVER_ERROR = "about:blank"; // RFC 9457 fallback
  private static final String BASE = "/problems";
  public static final String PATH_PARAM_INVALID_INTEGER = BASE + "/path-param-invalid-integer";
  public static final String PATH_PARAM_INVALID_LONG = BASE + "/path-param-invalid-long";
  public static final String PATH_PARAM_MISSING = BASE + "/path-param-missing";
  public static final String CLIENT_ERROR = BASE + "/client-error";

  public static final String QUERY_PARAM_MISSING = BASE + "/query-param-missing";
  public static final String QUERY_PARAM_INVALID_INTEGER = BASE + "/query-param-invalid-integer";
  public static final String QUERY_PARAM_INVALID_LONG = BASE + "/query-param-invalid-long";

  private ProblemDetailsTypes() {
  }
}