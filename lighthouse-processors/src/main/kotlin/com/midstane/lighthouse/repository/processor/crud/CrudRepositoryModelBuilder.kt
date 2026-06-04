package com.midstane.lighthouse.repository.processor.crud

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import java.io.File

class CrudRepositoryModelBuilder(
    private val logger: KSPLogger,
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
        val idPropertyName = annotation.requireString("primaryProperty", repository) ?: return null
        val configuredName = annotation.stringOrDefault("generatedName")

        val entity = entityType.declaration as? KSClassDeclaration ?: run {
            logger.error("The entity argument must point to a class.", repository)
            return null
        }

        val entityProperties = entity.getAllProperties().associateBy { it.simpleName.asString() }
        val annotatedPrimaryKeys = entity.primaryConstructor
            ?.parameters
            ?.mapNotNull { parameter ->
                val name = parameter.name?.asString() ?: return@mapNotNull null
                val propertyDeclaration = entityProperties[name]
                name.takeIf { parameter.hasAnnotation(PRIMARY_KEY) || propertyDeclaration.hasAnnotation(PRIMARY_KEY) }
            }
            .orEmpty()
        if (annotatedPrimaryKeys.size > 1) {
            logger.error("Only one entity constructor property can be annotated with @PrimaryKey.", entity)
            return null
        }
        val primaryPropertyName = annotatedPrimaryKeys.singleOrNull() ?: idPropertyName
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
                val column = type.toExposedColumn(declaration, hasText)
                if (column == null) {
                    logger.error(
                        "Unsupported CRUD property type '${type.qualifiedName}'. Supported types: String, Int, Long, Boolean, UUID, and enums.",
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

        val idProperty = properties.firstOrNull { it.name == primaryPropertyName } ?: run {
            logger.error("No entity constructor property named '$primaryPropertyName' was found.", entity)
            return null
        }

        return CrudRepositoryModel(
            packageName = repository.packageName.asString(),
            repositoryName = repository.simpleName.asString(),
            generatedName = configuredName.ifBlank { "Generated${repository.simpleName.asString()}" },
            entity = entity.toKotlinType(),
            bindingScope = bindingScopeType.toKotlinType(),
            tableName = tableName,
            idProperty = idProperty,
            properties = properties,
            finders = repository.declarations
                .filterIsInstance<KSFunctionDeclaration>()
                .mapNotNull { function -> function.toFinder(properties) }
                .toList(),
            primaryKeyIsInherited = idProperty.isInheritedLightTableId(),
        )
    }

    private fun KSFunctionDeclaration.toFinder(properties: List<EntityProperty>): Finder? {
        val propertyName = simpleName.asString()
            .takeIf { it.startsWith("findBy") }
            ?.removePrefix("findBy")
            ?: return null

        if (parameters.size != 1) return null

        val property = properties.firstOrNull { it.name.equals(propertyName, ignoreCase = true) }
            ?: return null

        return Finder(
            functionName = simpleName.asString(),
            parameterName = parameters.single().name?.asString() ?: return null,
            property = property,
        )
    }

    private fun KSType.toKotlinType(): KotlinType {
        return declaration.toKotlinType()
    }

    private fun KSDeclaration.toKotlinType(): KotlinType {
        return KotlinType(qualifiedName?.asString() ?: simpleName.asString())
    }

    private fun KotlinType.toExposedColumn(declaration: KSDeclaration, text: Boolean): ExposedColumn? {
        return when (qualifiedName) {
            "kotlin.Boolean" -> ExposedColumn.Boolean
            "kotlin.Int" -> ExposedColumn.Int
            "kotlin.Long" -> ExposedColumn.Long
            "kotlin.String" -> if (text) ExposedColumn.Text else ExposedColumn.String
            "java.util.UUID" -> ExposedColumn.Uuid
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
        const val PRIMARY_KEY =
            "com.midstane.lighthouse.repository.annotations.PrimaryKey"
    }
}
