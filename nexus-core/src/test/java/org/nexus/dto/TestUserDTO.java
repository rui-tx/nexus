package org.nexus.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Test user DTO for end-to-end testing.
 */
public class TestUserDTO {

  private String username;
  private String password;

  @JsonProperty
  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @JsonProperty
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
