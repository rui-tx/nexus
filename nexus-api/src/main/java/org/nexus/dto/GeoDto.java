package org.nexus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GeoDto(
    @JsonProperty("lat") String latitude,
    @JsonProperty("lng") String longitude
) {

}
