package org.nexus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PathParamRequestTestDTO(
    @JsonProperty("foo") int foo,
    @JsonProperty("bar") String bar) {

}