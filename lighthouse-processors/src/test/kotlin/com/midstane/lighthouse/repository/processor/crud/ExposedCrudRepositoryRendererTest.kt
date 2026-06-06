package com.midstane.lighthouse.repository.processor.crud

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExposedCrudRepositoryRendererTest {
    private val renderer = ExposedCrudRepositoryRenderer()

    @Test
    fun `renders complete crud repository with finder and supported column types`() {
        val source = renderer.render(model())

        assertContains(source, "package com.midstane.lighthouse.repository")
        assertContains(source, "import com.midstane.lighthouse.dependency.LightHouseScope")
        assertContains(source, "@ContributesBinding(LightHouseScope::class, binding<UserRepository>())")
        assertContains(source, "class GeneratedUserRepository(")
        assertContains(source, ") : UserRepository {")
        assertContains(source, "override suspend fun save(entity: UserDto): UserDto = transaction {")
        assertContains(source, "val updatedRows = UserDtoTable.updateReturning( where = { UserDtoTable.username eq entity.username })")
        assertContains(source, "override suspend fun findById(id: kotlin.String): UserDto? = transaction {")
        assertContains(source, "override suspend fun findAll(): List<UserDto> = transaction {")
        assertContains(source, "override suspend fun deleteById(id: kotlin.String)")
        assertContains(source, "UserDtoTable.deleteWhere { UserDtoTable.username eq id }")
        assertContains(source, "override suspend fun findByUsername(username: kotlin.String): UserDto? = transaction {")
        assertContains(source, "override suspend fun findByAge(age: kotlin.Int): UserDto? = transaction {")
        assertContains(source, ".where { UserDtoTable.age eq age }")
        assertContains(source, "override suspend fun findAllByAge(age: kotlin.Int): List<UserDto> = transaction {")
        assertContains(source, "            .map(::toEntity)")
        assertContains(source, "override suspend fun existsByUsername(username: kotlin.String): kotlin.Boolean = transaction {")
        assertContains(source, "            .isNotEmpty()")
        assertContains(source, "override suspend fun countByAge(age: kotlin.Int): kotlin.Long = transaction {")
        assertContains(source, "            .toLong()")
        assertContains(source, "private fun toEntity(row: ResultRow): UserDto")
        assertContains(source, "private object UserDtoTable : LongIdTable(\"users\")")
        assertContains(source, "val username = varchar(\"username\", 255)")
        assertContains(source, "val age = integer(\"age\")")
        assertContains(source, "val accountId = long(\"accountId\")")
        assertContains(source, "val active = bool(\"active\")")
        assertContains(source, "val createdAt = timestamp(\"created_at\").defaultExpression(CurrentTimestamp)")
        assertContains(source, "val updatedAt = timestamp(\"updated_at\").defaultExpression(CurrentTimestamp)")
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

        assertContains(source, "LongIdTable(\"user\\\\records\\\"archive\")")
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
        assertContains(source, "val data = UserDtoTable.insertReturning {")
        assertContains(source, "return@transaction data.singleOrNull()?.let { toEntity(it) } ?: entity")
        assertContains(source, "val email = varchar(\"email\", 255).uniqueIndex()")
        assertContains(source, "val bio = text(\"bio\")")
        assertContains(
            source,
            "val status = enumerationByName<com.midstane.lighthouse.entity.UserStatus>(\"status\", 255).default(UserStatus.Active)",
        )
        assertContains(source, "import com.midstane.lighthouse.entity.UserStatus")
        assertContains(source, "override val primaryKey = PrimaryKey(id)")
        assertContains(source, "val updatedRows = UserDtoTable.updateReturning( where = { UserDtoTable.id eq entity.id }) {")
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
        assertContains(source, "val data = UserDtoTable.insertReturning {")
        assertContains(source, "return@transaction data.singleOrNull()?.let { toEntity(it) } ?: entity")
    }


    @Test
    fun `does not generate inherited LongIdTable long id column or primary key override`() {
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

        assertContains(source, "val updatedRows = UserDtoTable.updateReturning( where = { UserDtoTable.id eq entity.id })")
        assertContains(source, "val data = UserDtoTable.insertReturning {")
        assertContains(source, "return@transaction data.singleOrNull()?.let { toEntity(it) } ?: entity")
        assertContains(source, "it[UserDtoTable.name] = entity.name")
        assertContains(source, "id = row[UserDtoTable.id].value,")
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
    fun `maps automatic timestamps to entity but ignores them on create and update`() {
        val id = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false)
        val source = renderer.render(
            model(
                idProperty = id,
                properties = listOf(
                    id,
                    property("name", KotlinType("kotlin.String"), ExposedColumn.String),
                    property("createdAt", KotlinType("kotlin.String"), ExposedColumn.Timestamp),
                    property("updatedAt", KotlinType("kotlin.String"), ExposedColumn.Timestamp),
                ),
                finders = emptyList(),
                primaryKeyIsInherited = true,
            ),
        )

        assertContains(source, "createdAt = row[UserDtoTable.createdAt].toString(),")
        assertContains(source, "updatedAt = row[UserDtoTable.updatedAt].toString(),")
        assertContains(source, "val createdAt = timestamp(\"created_at\").defaultExpression(CurrentTimestamp)")
        assertContains(source, "val updatedAt = timestamp(\"updated_at\").defaultExpression(CurrentTimestamp)")
        assertContains(source, "it[UserDtoTable.updatedAt] = Clock.System.now()")
        assertFalse(source.contains("it[UserDtoTable.createdAt] = entity.createdAt"))
        assertFalse(source.contains("it[UserDtoTable.updatedAt] = entity.updatedAt"))
    }

    @Test
    fun `renders instant and custom timestamp columns`() {
        val id = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false)
        val source = renderer.render(
            model(
                idProperty = id,
                properties = listOf(
                    id,
                    property("createdAt", KotlinType("kotlin.time.Instant"), ExposedColumn.Timestamp),
                    property("lastLogin", KotlinType("kotlin.time.Instant"), ExposedColumn.Timestamp),
                ),
                finders = emptyList(),
                primaryKeyIsInherited = true,
            ),
        )

        assertContains(source, "createdAt = row[UserDtoTable.createdAt],")
        assertContains(source, "lastLogin = row[UserDtoTable.lastLogin],")
        assertContains(source, "val createdAt = timestamp(\"created_at\").defaultExpression(CurrentTimestamp)")
        assertContains(source, "val lastLogin = timestamp(\"lastLogin\")")
    }

    @Test
    fun `renders local date and nullable local date columns`() {
        val id = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false)
        val source = renderer.render(
            model(
                idProperty = id,
                properties = listOf(
                    id,
                    property("startDate", KotlinType("kotlinx.datetime.LocalDate"), ExposedColumn.Date),
                    property("endDate", KotlinType("kotlinx.datetime.LocalDate", nullable = true), ExposedColumn.Date),
                ),
                finders = emptyList(),
                primaryKeyIsInherited = true,
            ),
        )

        assertContains(source, "startDate = row[UserDtoTable.startDate],")
        assertContains(source, "endDate = row[UserDtoTable.endDate],")
        assertContains(source, "val startDate = date(\"startDate\")")
        assertContains(source, "val endDate = date(\"endDate\").nullable()")
        assertContains(source, "it[UserDtoTable.startDate] = entity.startDate")
        assertContains(source, "it[UserDtoTable.endDate] = entity.endDate")
    }

    @Test
    fun `derives simple names from qualified types`() {
        assertEquals("UserDto", KotlinType("com.midstane.lighthouse.dto.UserDto").simpleName)
        assertEquals("String", KotlinType("kotlin.String").simpleName)
        assertEquals("kotlin.String", KotlinType("kotlin.String").displayName)
        val nullableString = KotlinType("kotlin.String", nullable = true)
        assertTrue(nullableString.nullable)
        assertEquals(emptyList(), nullableString.arguments)
        assertEquals("kotlin.String?", nullableString.displayName)
        assertEquals(
            "kotlin.collections.List<kotlin.String>",
            KotlinType("kotlin.collections.List", arguments = listOf(KotlinType("kotlin.String"))).displayName,
        )
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
        finders: List<Finder> = listOf(
            Finder("findByUsername", "username", idProperty),
            Finder("findByAge", "age", properties.first { it.name == "age" }),
            Finder("findAllByAge", "age", properties.first { it.name == "age" }, DerivedQueryKind.FindAll),
            Finder("existsByUsername", "username", idProperty, DerivedQueryKind.Exists),
            Finder("countByAge", "age", properties.first { it.name == "age" }, DerivedQueryKind.Count),
        ),
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
