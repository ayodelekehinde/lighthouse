package com.midstane.lighthouse.mapper

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.dto.UserDto
import com.midstane.lighthouse.entity.Occupation
import com.midstane.lighthouse.entity.User
import com.midstane.lighthouse.repository.annotations.Mapper
import com.midstane.lighthouse.repository.annotations.Mapping

@Mapper(bindingScope = LightHouseScope::class)
interface UserMapper {
    @Mapping(target = "createdAt", expression = "\"\"")
    @Mapping(target = "updatedAt", expression = "\"\"")
    fun toUser(dto: UserDto): User

    @Mapping(target = "id", expression = "0L")
    @Mapping(target = "type", source = "dto.occupationType")
    fun toOccupation(dto: UserDto, userId: Long): Occupation

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "uuid", expression = "user.uuid.toString()")
    @Mapping(target = "occupationType", source = "occupation.type")
    fun toDto(user: User, occupation: Occupation): UserDto
}
