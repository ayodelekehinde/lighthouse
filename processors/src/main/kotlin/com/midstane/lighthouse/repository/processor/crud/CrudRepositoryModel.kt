package com.midstane.lighthouse.repository.processor.crud

data class CrudRepositoryModel(
    val packageName: String,
    val repositoryName: String,
    val generatedName: String,
    val entity: KotlinType,
    val bindingScope: KotlinType,
    val tableName: String,
    val idProperty: EntityProperty,
    val properties: List<EntityProperty>,
    val finders: List<Finder>,
) {
    val tableObjectName: String = "${entity.simpleName}Table"
    val tablePackageName: String = packageName.substringBeforeLast('.', packageName) + ".tables"
}

data class EntityProperty(
    val name: String,
    val type: KotlinType,
    val column: ExposedColumn,
    val options: ColumnOptions = ColumnOptions(),
    val generateColumn: Boolean = true,
)

data class Finder(
    val functionName: String,
    val parameterName: String,
    val parameterType: KotlinType,
    val property: EntityProperty,
    val kind: DerivedQueryKind = DerivedQueryKind.FindOne,
)

enum class DerivedQueryKind {
    FindOne,
    FindAll,
    Exists,
    Count,
}

data class KotlinType(
    val qualifiedName: String,
    val nullable: Boolean = false,
    val arguments: List<KotlinType> = emptyList(),
) {
    val simpleName: String = qualifiedName.substringAfterLast('.').removeSuffix("?")
    val displayName: String = buildString {
        append(qualifiedName)
        if (arguments.isNotEmpty()) {
            append(arguments.joinToString(prefix = "<", postfix = ">") { it.displayName })
        }
        if (nullable) append("?")
    }
}

data class ColumnOptions(
    val unique: Boolean = false,
    val autogenerate: Boolean = false,
    val defaultValue: String? = null,
)

sealed interface ExposedColumn {
    data object Boolean : ExposedColumn
    data object Int : ExposedColumn
    data object Long : ExposedColumn
    data object String : ExposedColumn
    data object Text : ExposedColumn
    data object Timestamp : ExposedColumn
    data object Date : ExposedColumn
    data object Uuid : ExposedColumn
    data class Enum(val type: KotlinType) : ExposedColumn
}
