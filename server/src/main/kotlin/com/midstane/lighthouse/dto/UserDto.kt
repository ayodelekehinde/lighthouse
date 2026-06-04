package com.midstane.lighthouse.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long = 0,
    val name: String,
    val email: String,
    val age: Int
)