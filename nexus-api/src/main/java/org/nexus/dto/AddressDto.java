package org.nexus.dto;

public record AddressDto(
    String street,
    String suite,
    String city,
    String zipcode,
    GeoDto geo
) {

}
