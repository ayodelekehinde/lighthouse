package com.midstane.lighthouse.repository

interface CrudRepository<E, ID> : Repository<E, ID> {
    suspend fun save(entity: E): E
    suspend fun findById(id: ID): E?
    suspend fun findAll(): List<E>
    suspend fun deleteById(id: ID)
}
