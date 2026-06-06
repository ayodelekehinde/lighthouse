package com.midstane.lighthouse.repository

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.entity.User
import com.midstane.lighthouse.repository.annotations.DataRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@DataRepository(
    entity = User::class,
    tableName = "users",
    bindingScope = LightHouseScope::class,
)
interface UserRepository : CrudRepository<User, Long> {
    suspend fun findByEmail(email: String): User?
    suspend fun findByAge(age: Int): User?
    suspend fun findByUuid(uuid: Uuid): User?
    suspend fun findAllByAge(age: Int): List<User>
    suspend fun findAllByEmail(email: String): List<User>
    suspend fun existsByEmail(email: String): Boolean
    suspend fun countByAge(age: Int): Long
}
