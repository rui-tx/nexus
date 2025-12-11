package org.nexus.commons.enums;

public enum HttpMethod {
  OPTIONS,
  GET,
  HEAD,
  POST,
  PUT,
  PATCH,
  DELETE,
  TRACE,
  CONNECT;

  @Override
  public String toString() {
    return name();
  }
}
