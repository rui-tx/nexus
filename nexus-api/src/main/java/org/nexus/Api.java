package org.nexus;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.nexus.annotations.Mapping;
import org.nexus.annotations.QueryParam;
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
  public Response<ApiDTO> testParams(
      int id,
      String name,
      @QueryParam("foo") List<Integer> foo,
      @QueryParam("bar") String bar) {

    Map<String, String> map = new LinkedHashMap<>();
    if (foo != null) {
      map.put("foo", foo.toString());
    }

    if (bar != null) {
      map.put("bar", bar.toString());
    }

    return new Response<>(200, new ApiDTO(id, name, map));
  }

  public record ApiDTO(
      @JsonProperty("id") int id,
      @JsonProperty("name") String name,
      @JsonProperty("query_params") Map<String, String> query) {

  }
}

