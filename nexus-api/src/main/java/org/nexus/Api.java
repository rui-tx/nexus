package org.nexus;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.nexus.annotations.Mapping;
import org.nexus.enums.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Api {

  private static final Logger LOGGER = LoggerFactory.getLogger(Api.class);
  private static final String ENDPOINT = "/api/v1";

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/heartbeat")
  public Response<String> pong() {
    return new Response<>(200, "up");
  }

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/throw")
  public Response<String> testThrowException() {
    throw new RuntimeException("Server error");
  }

  @Mapping(type = HttpMethod.GET, endpoint = ENDPOINT + "/path/:id/:name")
  public Response<TestDTO> testPathParams(int id, String name) {
    return new Response<>(200, new TestDTO(id, name));
  }

  public record TestDTO(
      @JsonProperty("id") int id,
      @JsonProperty("name") String name) {

  }
}

