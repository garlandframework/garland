package org.modulartestorchestrator.mapstruct.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.modulartestorchestrator.mapstruct.dto.PersonDto;
import org.modulartestorchestrator.mapstruct.entity.PersonEntity;

// uses = AddressMapper.class tells MapStruct to delegate address field mapping
// to AddressMapper automatically — no explicit @Mapping needed for nested objects.
@Mapper(uses = AddressMapper.class)
public interface PersonMapper {

    PersonMapper INSTANCE = Mappers.getMapper(PersonMapper.class);

    @Mapping(target = "id",                 ignore = true)
    @Mapping(source = "firstname",          target = "first")
    @Mapping(source = "lastname",           target = "last")
    @Mapping(source = "birthday",           target = "bornAt")
    @Mapping(source = "badgeNum",           target = "identificationCardId")
    @Mapping(target = "createdAt",          ignore = true)
    @Mapping(target = "modifiedAt",         ignore = true)
    @Mapping(source = "address",            target = "personAddress")  // field rename; AddressMapper handles type conversion
    PersonEntity toEntity(PersonDto dto);

    @Mapping(target = "id",         ignore = true)
    @Mapping(source = "first",      target = "firstname")
    @Mapping(source = "last",       target = "lastname")
    @Mapping(source = "bornAt",     target = "birthday")
    @Mapping(source = "identificationCardId", target = "badgeNum")
    @Mapping(source = "personAddress",        target = "address")      // reverse rename
    PersonDto toDto(PersonEntity entity);
}
