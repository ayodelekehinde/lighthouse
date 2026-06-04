package com.midstane.lighthouse.entity

import com.midstane.lighthouse.repository.annotations.Autogenerate
import com.midstane.lighthouse.repository.annotations.PrimaryKey
import com.midstane.lighthouse.repository.annotations.Unique
import java.time.OffsetDateTime
import java.util.UUID

data class User(
    val id: Long = 0,
    val name: String,
    @Unique val email: String,
    val age: Int,
//    val updatedAt: String,
//    val createdAt: String,
)
