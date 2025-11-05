package org.nexus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class NexusUtils {

  public static final ObjectMapper DF_MAPPER = new ObjectMapper();
  public static final ObjectMapper MAPPER_REFLECTION_CFG =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT);

}
