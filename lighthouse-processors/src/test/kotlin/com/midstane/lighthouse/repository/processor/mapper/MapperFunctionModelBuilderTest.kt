package com.midstane.lighthouse.repository.processor.mapper

import com.midstane.lighthouse.repository.processor.crud.KotlinType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MapperFunctionModelBuilderTest {
    private val builder = MapperFunctionModelBuilder()
    private val stringType = KotlinType("kotlin.String")
    private val longType = KotlinType("kotlin.Long")
    private val intType = KotlinType("kotlin.Int")
    private val userType = KotlinType("com.midstane.lighthouse.entity.User")
    private val dtoType = KotlinType("com.midstane.lighthouse.dto.UserDto")

    @Test
    fun `auto maps same name same type properties`() {
        val result = builder.build(
            spec(
                sourceProperties = listOf(
                    MapperProperty("id", longType),
                    MapperProperty("name", stringType),
                ),
                targetParameters = listOf(
                    MapperTargetParameter("id", longType),
                    MapperTargetParameter("name", stringType),
                ),
            ),
        )

        val success = assertIs<MapperFunctionBuildResult.Success>(result)
        assertEquals(
            listOf(
                PropertyAssignment("id", "user.id"),
                PropertyAssignment("name", "user.name"),
            ),
            success.function.assignments,
        )
    }

    @Test
    fun `maps renamed and nested source properties`() {
        val result = builder.build(
            spec(
                parameters = listOf(
                    MapperSourceParameter(
                        name = "user",
                        type = userType,
                        properties = listOf(MapperProperty("id", longType)),
                    ),
                    MapperSourceParameter(
                        name = "occupation",
                        type = KotlinType("com.midstane.lighthouse.entity.Occupation"),
                        properties = listOf(
                            MapperProperty("type", KotlinType("com.midstane.lighthouse.entity.OccupationType")),
                        ),
                    ),
                ),
                targetParameters = listOf(
                    MapperTargetParameter("id", longType),
                    MapperTargetParameter("occupationType", KotlinType("com.midstane.lighthouse.entity.OccupationType")),
                ),
                mappings = listOf(RequestedMapping(target = "occupationType", source = "occupation.type")),
            ),
        )

        val success = assertIs<MapperFunctionBuildResult.Success>(result)
        assertEquals(
            listOf(
                PropertyAssignment("id", "user.id"),
                PropertyAssignment("occupationType", "occupation.type"),
            ),
            success.function.assignments,
        )
    }

    @Test
    fun `maps expression properties`() {
        val result = builder.build(
            spec(
                sourceProperties = listOf(MapperProperty("uuid", KotlinType("kotlin.uuid.Uuid"))),
                targetParameters = listOf(MapperTargetParameter("uuid", stringType)),
                mappings = listOf(RequestedMapping(target = "uuid", expression = "user.uuid.toString()")),
            ),
        )

        val success = assertIs<MapperFunctionBuildResult.Success>(result)
        assertEquals(listOf(PropertyAssignment("uuid", "user.uuid.toString()")), success.function.assignments)
    }

    @Test
    fun `auto maps same name scalar parameters`() {
        val result = builder.build(
            spec(
                parameters = listOf(
                    MapperSourceParameter("dto", dtoType, listOf(MapperProperty("name", stringType))),
                    MapperSourceParameter("userId", longType, emptyList()),
                ),
                targetParameters = listOf(
                    MapperTargetParameter("name", stringType),
                    MapperTargetParameter("userId", longType),
                ),
            ),
        )

        val success = assertIs<MapperFunctionBuildResult.Success>(result)
        assertEquals(
            listOf(
                PropertyAssignment("name", "dto.name"),
                PropertyAssignment("userId", "userId"),
            ),
            success.function.assignments,
        )
    }

    @Test
    fun `allows target default values to be omitted`() {
        val result = builder.build(
            spec(
                sourceProperties = emptyList(),
                targetParameters = listOf(MapperTargetParameter("name", stringType, hasDefault = true)),
            ),
        )

        val success = assertIs<MapperFunctionBuildResult.Success>(result)
        assertEquals(emptyList(), success.function.assignments)
    }

    @Test
    fun `rejects functions without source parameters`() {
        assertError(
            "Mapper function 'toDto' must declare at least one source parameter.",
            builder.build(spec(parameters = emptyList())),
        )
    }

    @Test
    fun `rejects missing target property mapping`() {
        assertError(
            "Mapper function 'toDto' cannot map target constructor property 'name'. Add a matching source property, @Mapping source, @Mapping expression, or default value.",
            builder.build(
                spec(
                    sourceProperties = emptyList(),
                    targetParameters = listOf(MapperTargetParameter("name", stringType)),
                ),
            ),
        )
    }

    @Test
    fun `rejects ambiguous source properties`() {
        assertError(
            "Mapper function 'toDto' target 'id' is ambiguous across multiple source parameters. Add an explicit @Mapping.",
            builder.build(
                spec(
                    parameters = listOf(
                        MapperSourceParameter("user", userType, listOf(MapperProperty("id", longType))),
                        MapperSourceParameter(
                            "occupation",
                            KotlinType("com.midstane.lighthouse.entity.Occupation"),
                            listOf(MapperProperty("id", longType)),
                        ),
                    ),
                    targetParameters = listOf(MapperTargetParameter("id", longType)),
                ),
            ),
        )
    }

    @Test
    fun `rejects mappings that set source and expression`() {
        assertError(
            "Mapper function 'toDto' mapping for 'name' cannot set both source and expression.",
            builder.build(
                spec(
                    mappings = listOf(RequestedMapping(target = "name", source = "user.name", expression = "user.name")),
                ),
            ),
        )
    }

    @Test
    fun `rejects source mapping type mismatch`() {
        assertError(
            "Mapper function 'toDto' mapping for 'age' must be kotlin.Int, but 'user.name' is kotlin.String.",
            builder.build(
                spec(
                    sourceProperties = listOf(MapperProperty("name", stringType)),
                    targetParameters = listOf(MapperTargetParameter("age", intType)),
                    mappings = listOf(RequestedMapping(target = "age", source = "user.name")),
                ),
            ),
        )
    }

    private fun assertError(message: String, result: MapperFunctionBuildResult) {
        val error = assertIs<MapperFunctionBuildResult.Error>(result)
        assertEquals(message, error.message)
    }

    private fun spec(
        parameters: List<MapperSourceParameter> = listOf(
            MapperSourceParameter(
                name = "user",
                type = userType,
                properties = listOf(MapperProperty("name", stringType)),
            ),
        ),
        sourceProperties: List<MapperProperty> = listOf(MapperProperty("name", stringType)),
        targetParameters: List<MapperTargetParameter> = listOf(MapperTargetParameter("name", stringType)),
        mappings: List<RequestedMapping> = emptyList(),
    ): MapperFunctionSpec {
        val effectiveParameters = if (parameters.size == 1 && parameters.single().name == "user") {
            listOf(parameters.single().copy(properties = sourceProperties))
        } else {
            parameters
        }
        return MapperFunctionSpec(
            name = "toDto",
            parameters = effectiveParameters,
            returnType = dtoType,
            targetParameters = targetParameters,
            mappings = mappings,
        )
    }
}
