package com.midstane.lighthouse.repository.processor.crud

import kotlin.test.*

class ExposedCrudRepositoryRendererTest {
    private val renderer = ExposedCrudRepositoryRenderer()

    @Test
    fun `renders complete crud repository with finder and supported column types`() {
        val model = model()
        val source = renderer.renderRepository(model)
        val tableSource = renderer.renderTable(model)

        assertContains(source, "package com.midstane.lighthouse.repository")
        assertContains(source, "import com.midstane.lighthouse.dependency.LightHouseScope")
        assertContains(source, "import com.midstane.lighthouse.tables.UserDtoTable")
        assertContains(source, "@ContributesBinding(LightHouseScope::class, binding<UserRepository>())")
        assertContains(source, "class GeneratedUserRepository(")
        assertContains(source, ") : UserRepository {")
        assertContains(source, "override suspend fun save(entity: UserDto): UserDto = transaction {")
        assertContains(source, "val updatedRows = UserDtoTable.updateReturning( where = { UserDtoTable.id eq entity.id })")
        assertContains(source, "override suspend fun findById(id: kotlin.Long): UserDto? = transaction {")
        assertContains(source, "override suspend fun findAll(): List<UserDto> = transaction {")
        assertContains(source, "override suspend fun deleteById(id: kotlin.Long)")
        assertContains(source, "UserDtoTable.deleteWhere { UserDtoTable.id eq id }")
        assertContains(source, "override suspend fun findByName(name: kotlin.String): UserDto? = transaction {")
        assertContains(source, "override suspend fun findByAge(age: kotlin.Int): UserDto? = transaction {")
        assertContains(source, ".where { UserDtoTable.age eq age }")
        assertContains(source, "override suspend fun findAllByAge(age: kotlin.Int): List<UserDto> = transaction {")
        assertContains(source, "            .map(::toEntity)")
        assertContains(source, "override suspend fun existsByName(name: kotlin.String): kotlin.Boolean = transaction {")
        assertContains(source, "            .isNotEmpty()")
        assertContains(source, "override suspend fun countByAge(age: kotlin.Int): kotlin.Long = transaction {")
        assertContains(source, "            .toLong()")
        assertContains(source, "private fun toEntity(row: ResultRow): UserDto")
        assertFalse(source.contains("LongIdTable"))
        assertContains(tableSource, "package com.midstane.lighthouse.tables")
        assertContains(tableSource, "internal object UserDtoTable : LongIdTable(\"users\")")
        assertContains(tableSource, "val name = varchar(\"name\", 255)")
        assertContains(tableSource, "val age = integer(\"age\")")
        assertContains(tableSource, "val accountId = long(\"accountId\")")
        assertContains(tableSource, "val active = bool(\"active\")")
        assertContains(tableSource, "val createdAt = timestamp(\"created_at\").defaultExpression(CurrentTimestamp)")
        assertContains(tableSource, "val updatedAt = timestamp(\"updated_at\").defaultExpression(CurrentTimestamp)")
        assertFalse(source.contains("override val primaryKey"))
    }

    @Test
    fun `escapes table names`() {
        val id = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false)
        val source = renderer.renderTable(
            model(
                tableName = "user\\records\"archive",
                idProperty = id,
                properties = listOf(id),
                finders = emptyList(),
            ),
        )

        assertContains(source, "LongIdTable(\"user\\\\records\\\"archive\")")
        assertFalse(source.contains("val id = long(\"id\")"))
    }

    @Test
    fun `renders unique text uuid autogenerate default and enum columns`() {
        val id = property(
            name = "id",
            type = KotlinType("kotlin.Long"),
            column = ExposedColumn.Long,
            generateColumn = false,
        )
        val model = model(
            idProperty = id,
            properties = listOf(
                id,
                property(
                    name = "uuid",
                    type = KotlinType("kotlin.uuid.Uuid"),
                    column = ExposedColumn.Uuid,
                    options = ColumnOptions(autogenerate = true),
                ),
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
        )
        val source = renderer.renderRepository(model)
        val tableSource = renderer.renderTable(model)

        assertContains(tableSource, "val uuid = uuid(\"uuid\").autoGenerate()")
        assertContains(source, "val data = UserDtoTable.insertReturning {")
        assertContains(source, "return@transaction data.singleOrNull()?.let { toEntity(it) } ?: entity")
        assertContains(tableSource, "val email = varchar(\"email\", 255).uniqueIndex()")
        assertContains(tableSource, "val bio = text(\"bio\")")
        assertContains(
            tableSource,
            "val status = enumerationByName<com.midstane.lighthouse.entity.UserStatus>(\"status\", 255).default(UserStatus.Active)",
        )
        assertContains(tableSource, "import com.midstane.lighthouse.entity.UserStatus")
        assertContains(tableSource, "@file:OptIn(ExperimentalUuidApi::class)")
        assertContains(source, "val updatedRows = UserDtoTable.updateReturning( where = { UserDtoTable.id eq entity.id }) {")
        assertContains(source, "it[UserDtoTable.email] = entity.email")
        assertContains(source, "it[UserDtoTable.bio] = entity.bio")
        assertContains(source, "it[UserDtoTable.status] = entity.status")
        assertFalse(source.contains("it[UserDtoTable.id] = entity.id"))
        assertFalse(source.contains("it[UserDtoTable.uuid] = entity.uuid"))
    }

    @Test
    fun `renders non id autogenerate integer column`() {
        val id = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false)
        val source = renderer.renderTable(
            model(
                idProperty = id,
                properties = listOf(
                    id,
                    property(
                        name = "sequence",
                        type = KotlinType("kotlin.Int"),
                        column = ExposedColumn.Int,
                        options = ColumnOptions(autogenerate = true),
                    ),
                ),
                finders = emptyList(),
            ),
        )

        assertContains(source, "val sequence = integer(\"sequence\").autoIncrement()")
        assertFalse(source.contains(".default(1)"))
    }

    @Test
    fun `does not render default values for autogenerate columns`() {
        val id = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false)
        val source = renderer.renderTable(
            model(
                idProperty = id,
                properties = listOf(
                    id,
                    property(
                        name = "code",
                        type = KotlinType("kotlin.Int"),
                        column = ExposedColumn.Int,
                        options = ColumnOptions(autogenerate = true, defaultValue = "1"),
                    ),
                ),
                finders = emptyList(),
            ),
        )

        assertContains(source, "val code = integer(\"code\").autoIncrement()")
        assertFalse(source.contains(".default(1)"))
    }

    @Test
    fun `does not generate LongIdTable long id column or primary key override`() {
        val id = property(
            name = "id",
            type = KotlinType("kotlin.Long"),
            column = ExposedColumn.Long,
            generateColumn = false,
        )
        val model = model(
            idProperty = id,
            properties = listOf(
                id,
                property("name", KotlinType("kotlin.String"), ExposedColumn.String),
            ),
            finders = emptyList(),
        )
        val source = renderer.renderRepository(model)
        val tableSource = renderer.renderTable(model)

        assertContains(source, "val updatedRows = UserDtoTable.updateReturning( where = { UserDtoTable.id eq entity.id })")
        assertContains(source, "val data = UserDtoTable.insertReturning {")
        assertContains(source, "return@transaction data.singleOrNull()?.let { toEntity(it) } ?: entity")
        assertContains(source, "it[UserDtoTable.name] = entity.name")
        assertContains(source, "id = row[UserDtoTable.id].value,")
        assertContains(tableSource, "val name = varchar(\"name\", 255)")
        assertFalse(tableSource.contains("val id = long(\"id\")"))
        assertFalse(source.contains("it[UserDtoTable.id] = entity.id"))
        assertFalse(tableSource.contains("override val primaryKey"))
    }

    @Test
    fun `maps automatic timestamps to entity but ignores them on create and update`() {
        val id = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false)
        val model = model(
            idProperty = id,
            properties = listOf(
                id,
                property("name", KotlinType("kotlin.String"), ExposedColumn.String),
                property("createdAt", KotlinType("kotlin.String"), ExposedColumn.Timestamp),
                property("updatedAt", KotlinType("kotlin.String"), ExposedColumn.Timestamp),
            ),
            finders = emptyList(),
        )
        val source = renderer.renderRepository(model)
        val tableSource = renderer.renderTable(model)

        assertContains(source, "createdAt = row[UserDtoTable.createdAt].toString(),")
        assertContains(source, "updatedAt = row[UserDtoTable.updatedAt].toString(),")
        assertContains(tableSource, "val createdAt = timestamp(\"created_at\").defaultExpression(CurrentTimestamp)")
        assertContains(tableSource, "val updatedAt = timestamp(\"updated_at\").defaultExpression(CurrentTimestamp)")
        assertContains(source, "it[UserDtoTable.updatedAt] = Clock.System.now()")
        assertFalse(source.contains("it[UserDtoTable.createdAt] = entity.createdAt"))
        assertFalse(source.contains("it[UserDtoTable.updatedAt] = entity.updatedAt"))
    }

    @Test
    fun `renders instant and custom timestamp columns`() {
        val id = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false)
        val model = model(
            idProperty = id,
            properties = listOf(
                id,
                property("createdAt", KotlinType("kotlin.time.Instant"), ExposedColumn.Timestamp),
                property("lastLogin", KotlinType("kotlin.time.Instant"), ExposedColumn.Timestamp),
            ),
            finders = emptyList(),
        )
        val source = renderer.renderRepository(model)
        val tableSource = renderer.renderTable(model)

        assertContains(source, "createdAt = row[UserDtoTable.createdAt],")
        assertContains(source, "lastLogin = row[UserDtoTable.lastLogin],")
        assertContains(tableSource, "val createdAt = timestamp(\"created_at\").defaultExpression(CurrentTimestamp)")
        assertContains(tableSource, "val lastLogin = timestamp(\"lastLogin\")")
    }

    @Test
    fun `renders local date and nullable local date columns`() {
        val id = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false)
        val model = model(
            idProperty = id,
            properties = listOf(
                id,
                property("startDate", KotlinType("kotlinx.datetime.LocalDate"), ExposedColumn.Date),
                property("endDate", KotlinType("kotlinx.datetime.LocalDate", nullable = true), ExposedColumn.Date),
            ),
            finders = emptyList(),
        )
        val source = renderer.renderRepository(model)
        val tableSource = renderer.renderTable(model)

        assertContains(source, "startDate = row[UserDtoTable.startDate],")
        assertContains(source, "endDate = row[UserDtoTable.endDate],")
        assertContains(tableSource, "val startDate = date(\"startDate\")")
        assertContains(tableSource, "val endDate = date(\"endDate\").nullable()")
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

    private fun model(
        tableName: String = "users",
        idProperty: EntityProperty = property("id", KotlinType("kotlin.Long"), ExposedColumn.Long, generateColumn = false),
        properties: List<EntityProperty> = listOf(
            idProperty,
            property("name", KotlinType("kotlin.String"), ExposedColumn.String),
            property("age", KotlinType("kotlin.Int"), ExposedColumn.Int),
            property("accountId", KotlinType("kotlin.Long"), ExposedColumn.Long),
            property("active", KotlinType("kotlin.Boolean"), ExposedColumn.Boolean),
        ),
        finders: List<Finder> = listOf(
            Finder("findByName", "name", properties.first { it.name == "name" }),
            Finder("findByAge", "age", properties.first { it.name == "age" }),
            Finder("findAllByAge", "age", properties.first { it.name == "age" }, DerivedQueryKind.FindAll),
            Finder("existsByName", "name", properties.first { it.name == "name" }, DerivedQueryKind.Exists),
            Finder("countByAge", "age", properties.first { it.name == "age" }, DerivedQueryKind.Count),
        ),
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
