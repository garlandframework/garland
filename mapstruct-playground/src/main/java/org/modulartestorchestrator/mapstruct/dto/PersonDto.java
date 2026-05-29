package org.modulartestorchestrator.mapstruct.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PersonDto(
        UUID id,
        String firstname,
        String lastname,
        LocalDate birthday,
        String badgeNum,
        AddressDto address
) {}
