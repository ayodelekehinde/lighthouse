package com.midstane.lighthouse.repository

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.entity.User
import com.midstane.lighthouse.repository.annotations.DataRepository

@DataRepository(
    entity = User::class,
    tableName = "users",
    bindingScope = LightHouseScope::class,
)
interface UserRepository : CrudRepository<User, Long> {
    suspend fun findByEmail(email: String): User?
}
