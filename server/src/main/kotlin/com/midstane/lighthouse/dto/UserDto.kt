package com.midstane.lighthouse.dto

import com.midstane.lighthouse.entity.OccupationType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long = 0,
    val uuid: String = "",
    val name: String = "",
    val email: String = "",
    val age: Int = 0,
    val occupationType: OccupationType,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)