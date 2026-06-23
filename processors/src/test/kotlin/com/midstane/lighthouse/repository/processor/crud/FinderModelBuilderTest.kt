package com.midstane.lighthouse.repository.processor.crud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FinderModelBuilderTest {
    private val builder = FinderModelBuilder()
    private val entity = KotlinType("com.midstane.lighthouse.entity.User")
    private val age = EntityProperty("age", KotlinType("kotlin.Int"), ExposedColumn.Int)
    private val properties = listOf(age)

    @Test
    fun `builds single property finder`() {
        val result = builder.build(
            function(
                name = "findByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
                returnType = entity.copy(nullable = true),
            ),
            entity,
            properties,
        )

        val success = assertIs<FinderBuildResult.Success>(result)
        assertEquals(Finder("findByAge", "age", KotlinType("kotlin.Int"), age, DerivedQueryKind.FindOne), success.finder)
    }

    @Test
    fun `builds single property find all query`() {
        val result = builder.build(
            function(
                name = "findAllByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
                returnType = KotlinType("kotlin.collections.List", arguments = listOf(entity)),
            ),
            entity,
            properties,
        )

        val success = assertIs<FinderBuildResult.Success>(result)
        assertEquals(Finder("findAllByAge", "age", KotlinType("kotlin.Int"), age, DerivedQueryKind.FindAll), success.finder)
    }

    @Test
    fun `builds single property exists query`() {
        val result = builder.build(
            function(
                name = "existsByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
                returnType = KotlinType("kotlin.Boolean"),
            ),
            entity,
            properties,
        )

        val success = assertIs<FinderBuildResult.Success>(result)
        assertEquals(Finder("existsByAge", "age", KotlinType("kotlin.Int"), age, DerivedQueryKind.Exists), success.finder)
    }

    @Test
    fun `builds single property count query`() {
        val result = builder.build(
            function(
                name = "countByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
                returnType = KotlinType("kotlin.Long"),
            ),
            entity,
            properties,
        )

        val success = assertIs<FinderBuildResult.Success>(result)
        assertEquals(Finder("countByAge", "age", KotlinType("kotlin.Int"), age, DerivedQueryKind.Count), success.finder)
    }

    @Test
    fun `preserves nullable finder parameter type`() {
        val email = EntityProperty("email", KotlinType("kotlin.String", nullable = true), ExposedColumn.String)
        val result = builder.build(
            function(
                name = "findByEmail",
                parameters = listOf(FinderParameter("email", KotlinType("kotlin.String", nullable = true))),
                returnType = entity.copy(nullable = true),
            ),
            entity,
            listOf(email),
        )

        val success = assertIs<FinderBuildResult.Success>(result)
        assertEquals(
            Finder(
                functionName = "findByEmail",
                parameterName = "email",
                parameterType = KotlinType("kotlin.String", nullable = true),
                property = email,
                kind = DerivedQueryKind.FindOne,
            ),
            success.finder,
        )
    }

    @Test
    fun `ignores functions that are not finder declarations`() {
        val result = builder.build(
            function(name = "count"),
            entity,
            properties,
        )

        assertEquals(FinderBuildResult.Ignored, result)
    }

    @Test
    fun `rejects finder without uppercase property segment`() {
        val result = builder.build(
            function(name = "findByage"),
            entity,
            properties,
        )

        assertError(
            "Derived query 'findByage' must use one of: findBy<Property>, findAllBy<Property>, existsBy<Property>, countBy<Property>.",
            result,
        )
    }

    @Test
    fun `rejects finder without property segment`() {
        val result = builder.build(
            function(name = "findBy"),
            entity,
            properties,
        )

        assertError(
            "Derived query 'findBy' must use one of: findBy<Property>, findAllBy<Property>, existsBy<Property>, countBy<Property>.",
            result,
        )
    }

    @Test
    fun `rejects unknown property`() {
        val result = builder.build(
            function(name = "findByAgge"),
            entity,
            properties,
        )

        assertError("Derived query 'findByAgge' references unknown entity property 'agge'.", result)
    }

    @Test
    fun `rejects wrong parameter count`() {
        val result = builder.build(
            function(
                name = "findByAge",
                parameters = emptyList(),
                returnType = entity.copy(nullable = true),
            ),
            entity,
            properties,
        )

        assertError("Derived query 'findByAge' must declare exactly one parameter.", result)
    }

    @Test
    fun `rejects parameter type mismatch`() {
        val result = builder.build(
            function(
                name = "findByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Long"))),
                returnType = entity.copy(nullable = true),
            ),
            entity,
            properties,
        )

        assertError("Derived query 'findByAge' parameter 'age' must be kotlin.Int to match property 'age'.", result)
    }

    @Test
    fun `rejects non nullable entity return type`() {
        val result = builder.build(
            function(
                name = "findByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
                returnType = entity,
            ),
            entity,
            properties,
        )

        assertError("Derived query 'findByAge' must return com.midstane.lighthouse.entity.User?.", result)
    }

    @Test
    fun `rejects list return type for find one query`() {
        val result = builder.build(
            function(
                name = "findByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
                returnType = KotlinType("kotlin.collections.List"),
            ),
            entity,
            properties,
        )

        assertError("Derived query 'findByAge' must return com.midstane.lighthouse.entity.User?.", result)
    }

    @Test
    fun `rejects nullable entity return type for find all query`() {
        val result = builder.build(
            function(
                name = "findAllByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
                returnType = entity.copy(nullable = true),
            ),
            entity,
            properties,
        )

        assertError(
            "Derived query 'findAllByAge' must return kotlin.collections.List<com.midstane.lighthouse.entity.User>.",
            result,
        )
    }

    @Test
    fun `rejects nullable entity return type for exists query`() {
        val result = builder.build(
            function(
                name = "existsByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
                returnType = entity.copy(nullable = true),
            ),
            entity,
            properties,
        )

        assertError("Derived query 'existsByAge' must return kotlin.Boolean.", result)
    }

    @Test
    fun `rejects nullable entity return type for count query`() {
        val result = builder.build(
            function(
                name = "countByAge",
                parameters = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
                returnType = entity.copy(nullable = true),
            ),
            entity,
            properties,
        )

        assertError("Derived query 'countByAge' must return kotlin.Long.", result)
    }

    private fun function(
        name: String,
        parameters: List<FinderParameter> = listOf(FinderParameter("age", KotlinType("kotlin.Int"))),
        returnType: KotlinType = entity.copy(nullable = true),
    ): FinderFunction {
        return FinderFunction(name, parameters, returnType)
    }

    private fun assertError(message: String, result: FinderBuildResult) {
        val error = assertIs<FinderBuildResult.Error>(result)
        assertEquals(message, error.message)
    }
}
