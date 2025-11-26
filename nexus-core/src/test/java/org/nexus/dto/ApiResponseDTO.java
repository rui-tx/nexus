package org.nexus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponseDTO<T> {
  public String date;
  public int status;
  public T data;
}
