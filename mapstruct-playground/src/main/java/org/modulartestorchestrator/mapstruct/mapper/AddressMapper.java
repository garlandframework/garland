package org.modulartestorchestrator.mapstruct.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.modulartestorchestrator.mapstruct.dto.AddressDto;
import org.modulartestorchestrator.mapstruct.entity.AddressEntity;

@Mapper
public interface AddressMapper {

    AddressMapper INSTANCE = Mappers.getMapper(AddressMapper.class);

    @Mapping(target = "id",         ignore = true)
    @Mapping(source = "street",     target = "streetName")
    @Mapping(source = "city",       target = "cityName")
    @Mapping(source = "country",    target = "countryCode")
    @Mapping(source = "zipCode",    target = "postalCode")
    AddressEntity toEntity(AddressDto dto);

    @Mapping(target = "street",     source = "streetName")
    @Mapping(target = "city",       source = "cityName")
    @Mapping(target = "country",    source = "countryCode")
    @Mapping(target = "zipCode",    source = "postalCode")
    AddressDto toDto(AddressEntity entity);
}
