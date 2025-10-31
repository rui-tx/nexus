package org.nexus.interfaces;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 9457 Problem Details for HTTP APIs.
 * <a href="https://www.rfc-editor.org/rfc/rfc9457">Link</a>
 */
public sealed interface ProblemDetails permits ProblemDetails.Single, ProblemDetails.Multiple {

  default int getStatus() {
    return switch (this) {
      case Single s -> Objects.requireNonNullElse(s.status(), 500);
      case Multiple m -> m.problems().stream()
          .mapToInt(s -> Objects.requireNonNullElse(s.status(), 500))
          .findFirst()
          .orElse(500);
    };
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Single(
      @JsonProperty("type") String type,
      @JsonProperty("title") String title,
      @JsonProperty("status") Integer status,
      @JsonProperty("detail") String detail,
      @JsonProperty("instance") String instance,
      @JsonProperty Map<String, Object> extensions
  ) implements ProblemDetails {

    public Single(String type, String title, Integer status, String detail, String instance) {
      this(type, title, status, detail, instance, Map.of());
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Multiple(
      @JsonProperty("problems") List<Single> problems
  ) implements ProblemDetails {

  }
}