package org.nexus;

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
}

