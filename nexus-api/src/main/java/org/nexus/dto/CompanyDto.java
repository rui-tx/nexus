package org.nexus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CompanyDto(
    String name,
    @JsonProperty("catchPhrase") String catchPhrase,
    String bs
) {

}