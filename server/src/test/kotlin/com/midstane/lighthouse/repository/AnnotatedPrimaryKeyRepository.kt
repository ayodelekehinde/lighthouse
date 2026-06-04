package com.midstane.lighthouse.repository

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.repository.annotations.DataRepository
import com.midstane.lighthouse.repository.annotations.PrimaryKey

data class AnnotatedPrimaryKeyEntity(
    val id: Long,
    @param:PrimaryKey val slug: String,
    val name: String,
)

@DataRepository(
    entity = AnnotatedPrimaryKeyEntity::class,
    tableName = "annotated_primary_keys",
    bindingScope = LightHouseScope::class,
)
interface AnnotatedPrimaryKeyRepository : CrudRepository<AnnotatedPrimaryKeyEntity, String>
