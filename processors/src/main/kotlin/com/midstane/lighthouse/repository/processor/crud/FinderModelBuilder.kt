package com.midstane.lighthouse.repository.processor.crud

private val QUERY_PREFIXES = listOf(
    QueryPrefix("findAllBy", DerivedQueryKind.FindAll),
    QueryPrefix("findBy", DerivedQueryKind.FindOne),
    QueryPrefix("existsBy", DerivedQueryKind.Exists),
    QueryPrefix("countBy", DerivedQueryKind.Count),
)

class FinderModelBuilder {
    fun build(
        function: FinderFunction,
        entity: KotlinType,
        properties: List<EntityProperty>,
    ): FinderBuildResult {
        val parsedName = function.name.parseDerivedQueryName() ?: return FinderBuildResult.Ignored
        val propertySegment = parsedName.propertySegment

        if (propertySegment.isBlank() || !propertySegment.first().isUpperCase()) {
            return FinderBuildResult.Error(
                "Derived query '${function.name}' must use one of: findBy<Property>, findAllBy<Property>, existsBy<Property>, countBy<Property>.",
            )
        }

        val propertyName = propertySegment.first().lowercaseChar() + propertySegment.drop(1)
        val property = properties.firstOrNull { it.name == propertyName }
            ?: return FinderBuildResult.Error(
                "Derived query '${function.name}' references unknown entity property '$propertyName'.",
            )

        if (function.parameters.size != 1) {
            return FinderBuildResult.Error(
                "Derived query '${function.name}' must declare exactly one parameter.",
            )
        }

        val parameter = function.parameters.single()
        if (parameter.type.qualifiedName != property.type.qualifiedName) {
            return FinderBuildResult.Error(
                "Derived query '${function.name}' parameter '${parameter.name}' must be ${property.type.displayName} to match property '$propertyName'.",
            )
        }

        val expectedReturnType = parsedName.kind.returnType(entity)
        if (function.returnType != expectedReturnType) {
            return FinderBuildResult.Error(
                "Derived query '${function.name}' must return ${expectedReturnType.displayName}.",
            )
        }

        return FinderBuildResult.Success(
            Finder(
                functionName = function.name,
                parameterName = parameter.name,
                parameterType = parameter.type,
                property = property,
                kind = parsedName.kind,
            ),
        )
    }

    private fun String.parseDerivedQueryName(): ParsedDerivedQueryName? {
        val prefix = QUERY_PREFIXES.firstOrNull { startsWith(it.prefix) } ?: return null
        return ParsedDerivedQueryName(
            kind = prefix.kind,
            propertySegment = removePrefix(prefix.prefix),
        )
    }

    private fun DerivedQueryKind.returnType(entity: KotlinType): KotlinType {
        return when (this) {
            DerivedQueryKind.FindOne -> entity.copy(nullable = true)
            DerivedQueryKind.FindAll -> KotlinType("kotlin.collections.List", arguments = listOf(entity))
            DerivedQueryKind.Exists -> KotlinType("kotlin.Boolean")
            DerivedQueryKind.Count -> KotlinType("kotlin.Long")
        }
    }

    private data class ParsedDerivedQueryName(
        val kind: DerivedQueryKind,
        val propertySegment: String,
    )
}

private data class QueryPrefix(
    val prefix: String,
    val kind: DerivedQueryKind,
)

data class FinderFunction(
    val name: String,
    val parameters: List<FinderParameter>,
    val returnType: KotlinType,
)

data class FinderParameter(
    val name: String,
    val type: KotlinType,
)

sealed interface FinderBuildResult {
    data class Success(val finder: Finder) : FinderBuildResult
    data class Error(val message: String) : FinderBuildResult
    data object Ignored : FinderBuildResult
}
