package org.modulartestorchestrator.mapstruct.dto;

public record AddressDto(
        String street,
        String city,
        String country,
        String zipCode
) {}
