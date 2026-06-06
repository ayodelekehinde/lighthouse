package com.midstane.lighthouse.entity

import com.midstane.lighthouse.repository.annotations.Unique
import kotlinx.datetime.LocalDate


data class Occupation(
    val id: Long = 0,
    val type: OccupationType = OccupationType.UnEmployed,
    @Unique val userId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate?,
)

enum class OccupationType {
    Engineer, Nurse, UnEmployed, Teacher, Doctor
}
