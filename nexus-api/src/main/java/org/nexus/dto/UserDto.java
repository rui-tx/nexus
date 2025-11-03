package org.nexus.dto;

public record UserDto(
    Long id,
    String name,
    String username,
    String email,
    AddressDto address,
    String phone,
    String website,
    CompanyDto company
) {

  public static UserDto emptyUser() {
    return new UserDto(
        0L, "", "", null, null, "", "", null);
  }

}