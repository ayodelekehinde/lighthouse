package com.midstane.lighthouse.repository.processor.crud

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ExposedCrudRepositoryRendererTest {
    private val renderer = ExposedCrudRepositoryRenderer()

    @Test
    fun `renders complete crud repository with finder and supported column types`() {
        val source = renderer.render(model())

        assertContains(source, "package com.midstane.lighthouse.repository")
        assertContains(source, "import com.midstane.lighthouse.dependency.LightHouseScope")
        assertContains(source, "import com.midstane.lighthouse.data.LightTable")
        assertContains(source, "@ContributesBinding(LightHouseScope::class, binding<UserRepository>())")
        assertContains(source, "class GeneratedUserRepository(")
        assertContains(source, ") : UserRepository {")
        assertContains(source, "override suspend fun save(entity: UserDto): UserDto = transaction {")
        assertContains(source, "val updatedRows = UserDtoTable.update({ UserDtoTable.username eq entity.username })")
        assertContains(source, "override suspend fun findById(id: kotlin.String): UserDto? = transaction {")
        assertContains(source, "override suspend fun findAll(): List<UserDto> = transaction {")
        assertContains(source, "override suspend fun deleteById(id: kotlin.String)")
        assertContains(source, "UserDtoTable.deleteWhere { UserDtoTable.username eq id }")
        assertContains(source, "override suspend fun findByUsername(username: kotlin.String): UserDto? = transaction {")
        assertContains(source, "private fun toEntity(row: ResultRow): UserDto")
        assertContains(source, "private object UserDtoTable : LightTable(\"users\")")
        assertContains(source, "val username = varchar(\"username\", 255)")
        assertContains(source, "val age = integer(\"age\")")
        assertContains(source, "val accountId = long(\"accountId\")")
        assertContains(source, "val active = bool(\"active\")")
        assertContains(source, "override val primaryKey = PrimaryKey(username)")
    }

    @Test
    fun `escapes table names`() {
        val id = property("username", KotlinType("kotlin.String"), ExposedColumn.String)
        val source = renderer.render(
            model(
                tableName = "user\\records\"archive",
                idProperty = id,
                properties = listOf(id),
                finders = emptyList(),
            ),
        )

        assertContains(source, "LightTable(\"user\\\\records\\\"archive\")")
        assertContains(source, "val username = varchar(\"username\", 255)")
    }

    @Test
    fun `renders unique text uuid autogenerate default and enum columns`() {
        val id = property(
            name = "id",
            type = KotlinType("java.util.UUID"),
            column = ExposedColumn.Uuid,
            options = ColumnOptions(autogenerate = true),
        )
        val source = renderer.render(
            model(
                idProperty = id,
                properties = listOf(
                    id,
                    property(
                        name = "email",
                        type = KotlinType("kotlin.String"),
                        column = ExposedColumn.String,
                        options = ColumnOptions(unique = true),
                    ),
                    property(
                        name = "bio",
                        type = KotlinType("kotlin.String"),
                        column = ExposedColumn.Text,
                    ),
                    property(
                        name = "status",
                        type = KotlinType("com.midstane.lighthouse.entity.UserStatus"),
                        column = ExposedColumn.Enum(KotlinType("com.midstane.lighthouse.entity.UserStatus")),
                        options = ColumnOptions(defaultValue = "UserStatus.Active"),
                    ),
                ),
                finders = emptyList(),
            ),
        )

        assertContains(source, "val id = uuid(\"id\").autoGenerate()")
        assertContains(source, "val insertedRow = UserDtoTable.insertReturning(listOf(UserDtoTable.id))")
        assertContains(source, "id = insertedRow[UserDtoTable.id],")
        assertContains(source, "val email = varchar(\"email\", 255).uniqueIndex()")
        assertContains(source, "val bio = text(\"bio\")")
        assertContains(
            source,
            "val status = enumerationByName<com.midstane.lighthouse.entity.UserStatus>(\"status\", 255).default(UserStatus.Active)",
        )
        assertContains(source, "override val primaryKey = PrimaryKey(id)")
        assertContains(source, "val updatedRows = UserDtoTable.update({ UserDtoTable.id eq entity.id }) {")
        assertContains(source, "it[UserDtoTable.email] = entity.email")
        assertContains(source, "it[UserDtoTable.bio] = entity.bio")
        assertContains(source, "it[UserDtoTable.status] = entity.status")
        assertFalse(source.contains("it[UserDtoTable.id] = entity.id"))
    }

    @Test
    fun `renders integer autogenerate columns`() {
        val id = property(
            name = "id",
            type = KotlinType("kotlin.Int"),
            column = ExposedColumn.Int,
            options = ColumnOptions(autogenerate = true),
        )
        val source = renderer.render(
            model(
                idProperty = id,
                properties = listOf(id),
                finders = emptyList(),
            ),
        )

        assertContains(source, "val id = integer(\"id\").autoIncrement()")
    }

    @Test
    fun `renders custom autogenerated primary key`() {
        val customId = property(
            name = "customId",
            type = KotlinType("kotlin.Long"),
            column = ExposedColumn.Long,
            options = ColumnOptions(autogenerate = true),
        )
        val source = renderer.render(
            model(
                idProperty = customId,
                properties = listOf(
                    customId,
                    property("name", KotlinType("kotlin.String"), ExposedColumn.String),
                ),
                finders = emptyList(),
            ),
        )

        assertContains(source, "val customId = long(\"customId\").autoIncrement()")
        assertContains(source, "val insertedRow = UserDtoTable.insertReturning(listOf(UserDtoTable.customId))")
        assertContains(source, "customId = insertedRow[UserDtoTable.customId],")
    }


    @Test
    fun `does not generate inherited LightTable long id column or primary key override`() {
        val id = property(
            name = "id",
            type = KotlinType("kotlin.Long"),
            column = ExposedColumn.Long,
            generateColumn = false,
        )
        val source = renderer.render(
            model(
                idProperty = id,
                properties = listOf(
                    id,
                    property("name", KotlinType("kotlin.String"), ExposedColumn.String),
                ),
                finders = emptyList(),
                primaryKeyIsInherited = true,
            ),
        )

        assertContains(source, "val updatedRows = UserDtoTable.update({ UserDtoTable.id eq entity.id })")
        assertContains(source, "val insertedRow = UserDtoTable.insertReturning(listOf(UserDtoTable.id))")
        assertContains(source, "id = insertedRow[UserDtoTable.id],")
        assertContains(source, "name = entity.name,")
        assertContains(source, "id = row[UserDtoTable.id],")
        assertContains(source, "val name = varchar(\"name\", 255)")
        assertFalse(source.contains("val id = long(\"id\")"))
        assertFalse(source.contains("it[UserDtoTable.id] = entity.id"))
        assertFalse(source.contains("override val primaryKey = PrimaryKey(id)"))
    }

    @Test
    fun `renders primary key override only when primary key differs from inherited id`() {
        val slug = property("slug", KotlinType("kotlin.String"), ExposedColumn.String)
        val source = renderer.render(
            model(
                idProperty = slug,
                properties = listOf(
                    property(
                        name = "id",
                        type = KotlinType("kotlin.Long"),
                        column = ExposedColumn.Long,
                        generateColumn = false,
                    ),
                    slug,
                ),
                finders = emptyList(),
            ),
        )

        assertContains(source, "val slug = varchar(\"slug\", 255)")
        assertContains(source, "override val primaryKey = PrimaryKey(slug)")
        assertFalse(source.contains("val id = long(\"id\")"))
    }

    @Test
    fun `non-generated non-inherited columns use normal row access`() {
        val slug = property("slug", KotlinType("kotlin.String"), ExposedColumn.String)
        val source = renderer.render(
            model(
                idProperty = slug,
                properties = listOf(
                    property(
                        name = "legacyId",
                        type = KotlinType("kotlin.Long"),
                        column = ExposedColumn.Long,
                        generateColumn = false,
                    ),
                    slug,
                ),
                finders = emptyList(),
            ),
        )

        assertContains(source, "legacyId = row[UserDtoTable.legacyId],")
    }

    @Test
    fun `derives simple names from qualified types`() {
        assertEquals("UserDto", KotlinType("com.midstane.lighthouse.dto.UserDto").simpleName)
        assertEquals("String", KotlinType("kotlin.String").simpleName)
    }

    @Test
    fun `entity properties default to no column options`() {
        val property = EntityProperty("name", KotlinType("kotlin.String"), ExposedColumn.String)

        assertEquals(ColumnOptions(), property.options)
    }

    @Test
    fun `repository models default to explicit primary key rendering`() {
        val id = property("username", KotlinType("kotlin.String"), ExposedColumn.String)
        val model = CrudRepositoryModel(
            packageName = "com.midstane.lighthouse.repository",
            repositoryName = "UserRepository",
            generatedName = "GeneratedUserRepository",
            entity = KotlinType("com.midstane.lighthouse.dto.UserDto"),
            bindingScope = KotlinType("com.midstane.lighthouse.dependency.LightHouseScope"),
            tableName = "users",
            idProperty = id,
            properties = listOf(id),
            finders = emptyList(),
        )

        assertFalse(model.primaryKeyIsInherited)
    }

    private fun model(
        tableName: String = "users",
        idProperty: EntityProperty = property("username", KotlinType("kotlin.String"), ExposedColumn.String),
        properties: List<EntityProperty> = listOf(
            idProperty,
            property("age", KotlinType("kotlin.Int"), ExposedColumn.Int),
            property("accountId", KotlinType("kotlin.Long"), ExposedColumn.Long),
            property("active", KotlinType("kotlin.Boolean"), ExposedColumn.Boolean),
        ),
        finders: List<Finder> = listOf(Finder("findByUsername", "username", idProperty)),
        primaryKeyIsInherited: Boolean = false,
    ): CrudRepositoryModel {
        return CrudRepositoryModel(
            packageName = "com.midstane.lighthouse.repository",
            repositoryName = "UserRepository",
            generatedName = "GeneratedUserRepository",
            entity = KotlinType("com.midstane.lighthouse.dto.UserDto"),
            bindingScope = KotlinType("com.midstane.lighthouse.dependency.LightHouseScope"),
            tableName = tableName,
            idProperty = idProperty,
            properties = properties,
            finders = finders,
            primaryKeyIsInherited = primaryKeyIsInherited,
        )
    }

    private fun property(
        name: String,
        type: KotlinType,
        column: ExposedColumn,
        options: ColumnOptions = ColumnOptions(),
        generateColumn: Boolean = true,
    ): EntityProperty {
        return EntityProperty(
            name = name,
            type = type,
            column = column,
            options = options,
            generateColumn = generateColumn,
        )
    }
}
