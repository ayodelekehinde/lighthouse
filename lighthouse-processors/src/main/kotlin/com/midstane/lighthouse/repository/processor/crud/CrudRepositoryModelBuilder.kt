package com.midstane.lighthouse.repository.processor.crud

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.KSType
import java.io.File

class CrudRepositoryModelBuilder(
    private val logger: KSPLogger,
    private val finderModelBuilder: FinderModelBuilder = FinderModelBuilder(),
) {
    fun build(repository: KSClassDeclaration): CrudRepositoryModel? {
        if (repository.classKind != ClassKind.INTERFACE) {
            logger.error("@DataRepository can only be used on interfaces.", repository)
            return null
        }

        val annotation = repository.requireAnnotation(DATA_REPOSITORY) ?: return null
        val entityType = annotation.requireType("entity", repository) ?: return null
        val bindingScopeType = annotation.requireType("bindingScope", repository) ?: return null
        val tableName = annotation.requireString("tableName", repository) ?: return null
        val configuredName = annotation.stringOrDefault("generatedName")

        val entity = entityType.declaration as? KSClassDeclaration ?: run {
            logger.error("The entity argument must point to a class.", repository)
            return null
        }

        val entityProperties = entity.getAllProperties().associateBy { it.simpleName.asString() }
        val properties = entity.primaryConstructor
            ?.parameters
            ?.mapNotNull { parameter ->
                val name = parameter.name?.asString() ?: return@mapNotNull null
                val type = parameter.type.resolve().toKotlinType()
                val declaration = parameter.type.resolve().declaration
                val propertyDeclaration = entityProperties[name]
                val hasText = parameter.hasAnnotation(TEXT) || propertyDeclaration.hasAnnotation(TEXT)
                val hasUnique = parameter.hasAnnotation(UNIQUE) || propertyDeclaration.hasAnnotation(UNIQUE)
                val hasAutogenerate =
                    parameter.hasAnnotation(AUTOGENERATE) || propertyDeclaration.hasAnnotation(AUTOGENERATE)
                val column = type.toExposedColumn(declaration, hasText, name)
                if (column == null) {
                    logger.error(
                        "Unsupported CRUD property type '${type.qualifiedName}'. Supported types: String, Int, Long, Boolean, kotlin.time.Instant, kotlinx.datetime.LocalDate, UUID, and enums.",
                        entity,
                    )
                    return null
                }
                if (hasText && type.qualifiedName != "kotlin.String") {
                    logger.error("@Text can only be used on String properties.", parameter)
                    return null
                }
                if (hasAutogenerate && !column.supportsAutogenerate()) {
                    logger.error("@Autogenerate can only be used on Int, Long, and UUID properties.", parameter)
                    return null
                }
                val defaultValue = if (parameter.hasDefault) parameter.defaultValueExpression(name) else null
                EntityProperty(
                    name = name,
                    type = type,
                    column = column,
                    options = ColumnOptions(hasUnique, hasAutogenerate, defaultValue),
                    generateColumn = !name.isInheritedLightTableId(type),
                )
            }
            ?.toList()
            .orEmpty()

        if (properties.isEmpty()) {
            logger.error("Generated CRUD repositories require an entity primary constructor.", entity)
            return null
        }

        val idProperty = properties.firstOrNull { it.name == "id" && it.type.qualifiedName == "kotlin.Long" } ?: run {
            logger.error("Generated CRUD repositories require an entity constructor property named 'id' with type Long.", entity)
            return null
        }

        val repositoryEntity = entity.toKotlinType()
        val finderResults = repository.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .map { function -> function.toFinderResult(repositoryEntity, properties) }
            .toList()
        if (finderResults.any { it is FinderBuildResult.Error }) return null
        val finders = finderResults
            .filterIsInstance<FinderBuildResult.Success>()
            .map { it.finder }

        return CrudRepositoryModel(
            packageName = repository.packageName.asString(),
            repositoryName = repository.simpleName.asString(),
            generatedName = configuredName.ifBlank { "Generated${repository.simpleName.asString()}" },
            entity = repositoryEntity,
            bindingScope = bindingScopeType.toKotlinType(),
            tableName = tableName,
            idProperty = idProperty,
            properties = properties,
            finders = finders,
        )
    }

    private fun KSFunctionDeclaration.toFinderResult(entity: KotlinType, properties: List<EntityProperty>): FinderBuildResult {
        val result = finderModelBuilder.build(toFinderFunction() ?: return FinderBuildResult.Ignored, entity, properties)
        if (result is FinderBuildResult.Error) {
            logger.error(result.message, this)
        }
        return result
    }

    private fun KSFunctionDeclaration.toFinderFunction(): FinderFunction? {
        val returnType = returnType?.resolve()?.toKotlinType() ?: return null
        val finderParameters = parameters.map { parameter ->
            val name = parameter.name?.asString() ?: run {
                logger.error("Finder '${simpleName.asString()}' parameters must be named.", parameter)
                return null
            }
            FinderParameter(
                name = name,
                type = parameter.type.resolve().toKotlinType(),
            )
        }

        return FinderFunction(
            name = simpleName.asString(),
            parameters = finderParameters,
            returnType = returnType,
        )
    }

    private fun KSType.toKotlinType(): KotlinType {
        return declaration.toKotlinType(
            nullable = nullability == Nullability.NULLABLE,
            arguments = arguments.mapNotNull { argument -> argument.type?.resolve()?.toKotlinType() },
        )
    }

    private fun KSDeclaration.toKotlinType(
        nullable: Boolean = false,
        arguments: List<KotlinType> = emptyList(),
    ): KotlinType {
        return KotlinType(
            qualifiedName = qualifiedName?.asString() ?: simpleName.asString(),
            nullable = nullable,
            arguments = arguments,
        )
    }

    private fun KotlinType.toExposedColumn(declaration: KSDeclaration, text: Boolean, name: String): ExposedColumn? {
        if (name.isAutomaticTimestampProperty() && supportsAutomaticTimestampMapping()) {
            return ExposedColumn.Timestamp
        }

        return when (qualifiedName) {
            "kotlin.Boolean" -> ExposedColumn.Boolean
            "kotlin.Int" -> ExposedColumn.Int
            "kotlin.Long" -> ExposedColumn.Long
            "kotlin.String" -> if (text) ExposedColumn.Text else ExposedColumn.String
            "kotlin.time.Instant" -> ExposedColumn.Timestamp
            "kotlinx.datetime.LocalDate" -> ExposedColumn.Date
            "kotlin.uuid.Uuid" -> ExposedColumn.Uuid
            else -> {
                val classDeclaration = declaration as? KSClassDeclaration
                if (classDeclaration?.classKind == ClassKind.ENUM_CLASS) {
                    ExposedColumn.Enum(this)
                } else {
                    null
                }
            }
        }
    }

    private fun String.isAutomaticTimestampProperty(): Boolean {
        return this == "createdAt" || this == "updatedAt"
    }

    private fun KotlinType.supportsAutomaticTimestampMapping(): Boolean {
        return qualifiedName == "kotlin.String" || qualifiedName == "kotlin.time.Instant"
    }

    private fun ExposedColumn.supportsAutogenerate(): Boolean {
        return this == ExposedColumn.Int || this == ExposedColumn.Long || this == ExposedColumn.Uuid
    }

    private fun String.isInheritedLightTableId(type: KotlinType): Boolean {
        return this == "id" && type.qualifiedName == "kotlin.Long"
    }

    private fun EntityProperty.isInheritedLightTableId(): Boolean {
        return name.isInheritedLightTableId(type)
    }

    private fun KSAnnotation.requireType(name: String, target: KSAnnotated): KSType? {
        val value = arguments.firstOrNull { it.name?.asString() == name }?.value
        return value as? KSType ?: run {
            logger.error("Missing KClass annotation argument '$name'.", target)
            null
        }
    }

    private fun KSAnnotation.requireString(name: String, target: KSAnnotated): String? {
        val value = arguments.firstOrNull { it.name?.asString() == name }?.value as? String
        if (value.isNullOrBlank()) {
            logger.error("Annotation argument '$name' must not be blank.", target)
            return null
        }
        return value
    }

    private fun KSAnnotation.stringOrDefault(name: String): String {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? String ?: ""
    }

    private fun KSClassDeclaration.requireAnnotation(qualifiedName: String): KSAnnotation? {
        return annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }
            ?: run {
                logger.error("Missing @$qualifiedName annotation.", this)
                null
            }
    }

    private fun KSAnnotated?.hasAnnotation(qualifiedName: String): Boolean {
        return this?.annotations?.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
        } == true
    }

    private fun KSAnnotated.defaultValueExpression(name: String): String? {
        val location = location as? com.google.devtools.ksp.symbol.FileLocation ?: return null
        val source = runCatching { File(location.filePath).readText() }.getOrNull() ?: return null
        return source.defaultExpressionForParameter(name, location.lineNumber)
    }

    private fun String.defaultExpressionForParameter(name: String, lineNumber: Int): String? {
        val lineStart = lineSequence()
            .take((lineNumber - 1).coerceAtLeast(0))
            .sumOf { it.length + 1 }
            .coerceAtMost(length)
        val nameIndex = indexOf(name, startIndex = lineStart).takeIf { it >= 0 } ?: return null
        val equalsIndex = indexOf('=', startIndex = nameIndex).takeIf { it >= 0 } ?: return null
        return substring(equalsIndex + 1).takeDefaultExpression()
    }

    private fun String.takeDefaultExpression(): String? {
        var depth = 0
        var quote: Char? = null
        var escaped = false
        forEachIndexed { index, char ->
            if (quote != null) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == quote) {
                    quote = null
                }
                return@forEachIndexed
            }
            when (char) {
                '"', '\'' -> quote = char
                '(', '[', '{' -> depth++
                ')', ']', '}' -> {
                    if (depth == 0) return substring(0, index).trim().ifBlank { null }
                    depth--
                }
                ',' -> if (depth == 0) return substring(0, index).trim().ifBlank { null }
            }
        }
        return trim().ifBlank { null }
    }

    private companion object {
        const val DATA_REPOSITORY =
            "com.midstane.lighthouse.repository.annotations.DataRepository"
        const val UNIQUE =
            "com.midstane.lighthouse.repository.annotations.Unique"
        const val AUTOGENERATE =
            "com.midstane.lighthouse.repository.annotations.Autogenerate"
        const val TEXT =
            "com.midstane.lighthouse.repository.annotations.Text"
    }
}
