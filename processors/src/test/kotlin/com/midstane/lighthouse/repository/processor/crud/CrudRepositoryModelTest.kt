package com.midstane.lighthouse.repository.processor.crud

import kotlin.test.Test
import kotlin.test.assertEquals

class CrudRepositoryModelTest {
    @Test
    fun `derives table object and package names from model package and entity`() {
        val model = CrudRepositoryModel(
            packageName = "com.midstane.lighthouse.repository",
            repositoryName = "UserRepository",
            generatedName = "GeneratedUserRepository",
            entity = KotlinType("com.midstane.lighthouse.entity.User"),
            bindingScope = KotlinType("com.midstane.lighthouse.dependency.AppScope"),
            tableName = "users",
            idProperty = EntityProperty("id", KotlinType("kotlin.Long"), ExposedColumn.Long),
            properties = emptyList(),
            finders = emptyList(),
        )

        assertEquals("UserTable", model.tableObjectName)
        assertEquals("com.midstane.lighthouse.tables", model.tablePackageName)
    }

    @Test
    fun `uses original package as table package root when repository package has no parent`() {
        val model = CrudRepositoryModel(
            packageName = "repository",
            repositoryName = "UserRepository",
            generatedName = "GeneratedUserRepository",
            entity = KotlinType("User"),
            bindingScope = KotlinType("AppScope"),
            tableName = "users",
            idProperty = EntityProperty("id", KotlinType("kotlin.Long"), ExposedColumn.Long),
            properties = emptyList(),
            finders = emptyList(),
        )

        assertEquals("repository.tables", model.tablePackageName)
    }

    @Test
    fun `formats kotlin type display names with nested arguments and nullability`() {
        val type = KotlinType(
            qualifiedName = "kotlin.collections.Map",
            nullable = true,
            arguments = listOf(
                KotlinType("kotlin.String"),
                KotlinType(
                    qualifiedName = "kotlin.collections.List",
                    arguments = listOf(KotlinType("com.midstane.lighthouse.entity.User", nullable = true)),
                ),
            ),
        )

        assertEquals("Map", type.simpleName)
        assertEquals(
            "kotlin.collections.Map<kotlin.String, kotlin.collections.List<com.midstane.lighthouse.entity.User?>>?",
            type.displayName,
        )
    }
}
