package com.midstane.lighthouse.repository

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.entity.Occupation
import com.midstane.lighthouse.repository.annotations.DataRepository

@DataRepository(
    entity = Occupation::class,
    tableName = "occupation",
    bindingScope = LightHouseScope::class,
)
interface OccupationRepository: CrudRepository<Occupation, Long> {
    suspend fun findByUserId(userId: Long): Occupation?
}