package org.nexus;

import org.nexus.annotations.Mapping;
import org.nexus.enums.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Api {

  private static final Logger LOGGER = LoggerFactory.getLogger(Api.class);

  @Mapping(type = HttpMethod.GET, endpoint = "/heartbeat")
  public Response<String> pong() {
    return new Response<>(200, "up");
  }

  @Mapping(type = HttpMethod.GET, endpoint = "/external")
  public Response<String> externalTest() {
    throw new RuntimeException("Not implemented");
  }

}

