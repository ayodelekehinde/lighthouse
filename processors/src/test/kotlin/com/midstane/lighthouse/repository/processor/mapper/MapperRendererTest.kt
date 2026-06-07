package com.midstane.lighthouse.repository.processor.mapper

import com.midstane.lighthouse.repository.processor.crud.KotlinType
import kotlin.test.Test
import kotlin.test.assertContains

class MapperRendererTest {
    private val renderer = MapperRenderer()

    @Test
    fun `renders mapper implementation with metro binding and property assignments`() {
        val source = renderer.render(
            MapperModel(
                packageName = "com.midstane.lighthouse.mapper",
                mapperName = "UserMapper",
                generatedName = "GeneratedUserMapper",
                bindingScope = KotlinType("com.midstane.lighthouse.dependency.LightHouseScope"),
                functions = listOf(
                    MapperFunction(
                        name = "toDto",
                        parameters = listOf(
                            MapperParameter("user", KotlinType("com.midstane.lighthouse.entity.User")),
                            MapperParameter("occupation", KotlinType("com.midstane.lighthouse.entity.Occupation")),
                        ),
                        returnType = KotlinType("com.midstane.lighthouse.dto.UserDto"),
                        assignments = listOf(
                            PropertyAssignment("id", "user.id"),
                            PropertyAssignment("uuid", "user.uuid.toString()"),
                            PropertyAssignment("occupationType", "occupation.type"),
                        ),
                    ),
                ),
            ),
        )

        assertContains(source, "package com.midstane.lighthouse.mapper")
        assertContains(source, "import dev.zacsweers.metro.ContributesBinding")
        assertContains(
            source,
            "@ContributesBinding(com.midstane.lighthouse.dependency.LightHouseScope::class, binding<UserMapper>())",
        )
        assertContains(source, "@Inject")
        assertContains(source, "class GeneratedUserMapper : UserMapper")
        assertContains(
            source,
            "override fun toDto(user: com.midstane.lighthouse.entity.User, occupation: com.midstane.lighthouse.entity.Occupation): com.midstane.lighthouse.dto.UserDto",
        )
        assertContains(source, "return com.midstane.lighthouse.dto.UserDto(")
        assertContains(source, "id = user.id,")
        assertContains(source, "uuid = user.uuid.toString(),")
        assertContains(source, "occupationType = occupation.type,")
    }

    @Test
    fun `renders file opt in when mapper function uses kotlin uuid`() {
        val source = renderer.render(
            MapperModel(
                packageName = "com.midstane.lighthouse.mapper",
                mapperName = "UuidMapper",
                generatedName = "GeneratedUuidMapper",
                bindingScope = KotlinType("com.midstane.lighthouse.dependency.LightHouseScope"),
                functions = listOf(
                    MapperFunction(
                        name = "toString",
                        parameters = listOf(MapperParameter("uuid", KotlinType("kotlin.uuid.Uuid"))),
                        returnType = KotlinType("kotlin.String"),
                        assignments = emptyList(),
                    ),
                ),
            ),
        )

        assertContains(source, "@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)")
    }

    @Test
    fun `renders file opt in when model explicitly requires uuid opt in`() {
        val source = renderer.render(
            MapperModel(
                packageName = "com.midstane.lighthouse.mapper",
                mapperName = "UserMapper",
                generatedName = "GeneratedUserMapper",
                bindingScope = KotlinType("com.midstane.lighthouse.dependency.LightHouseScope"),
                functions = emptyList(),
                requiresExperimentalUuidOptIn = true,
            ),
        )

        assertContains(source, "@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)")
    }

    @Test
    fun `renders file opt in when mapper function parameter contains uuid type argument`() {
        val source = renderer.render(
            MapperModel(
                packageName = "com.midstane.lighthouse.mapper",
                mapperName = "UuidMapper",
                generatedName = "GeneratedUuidMapper",
                bindingScope = KotlinType("com.midstane.lighthouse.dependency.LightHouseScope"),
                functions = listOf(
                    MapperFunction(
                        name = "toString",
                        parameters = listOf(
                            MapperParameter(
                                "uuids",
                                KotlinType(
                                    qualifiedName = "kotlin.collections.List",
                                    arguments = listOf(KotlinType("kotlin.uuid.Uuid")),
                                ),
                            ),
                        ),
                        returnType = KotlinType("kotlin.String"),
                        assignments = emptyList(),
                    ),
                ),
            ),
        )

        assertContains(source, "@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)")
    }

    @Test
    fun `renders file opt in when mapper function return type is uuid`() {
        val source = renderer.render(
            MapperModel(
                packageName = "com.midstane.lighthouse.mapper",
                mapperName = "UuidMapper",
                generatedName = "GeneratedUuidMapper",
                bindingScope = KotlinType("com.midstane.lighthouse.dependency.LightHouseScope"),
                functions = listOf(
                    MapperFunction(
                        name = "toUuid",
                        parameters = emptyList(),
                        returnType = KotlinType("kotlin.uuid.Uuid"),
                        assignments = emptyList(),
                    ),
                ),
            ),
        )

        assertContains(source, "@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)")
    }

    @Test
    fun `renders multiple mapper functions with spacing`() {
        val source = renderer.render(
            MapperModel(
                packageName = "com.midstane.lighthouse.mapper",
                mapperName = "UserMapper",
                generatedName = "GeneratedUserMapper",
                bindingScope = KotlinType("com.midstane.lighthouse.dependency.LightHouseScope"),
                functions = listOf(
                    MapperFunction(
                        name = "first",
                        parameters = emptyList(),
                        returnType = KotlinType("com.midstane.lighthouse.dto.UserDto"),
                        assignments = emptyList(),
                    ),
                    MapperFunction(
                        name = "second",
                        parameters = emptyList(),
                        returnType = KotlinType("com.midstane.lighthouse.dto.UserDto"),
                        assignments = emptyList(),
                    ),
                ),
            ),
        )

        assertContains(source, "    }\n\n    override fun second")
    }
}
