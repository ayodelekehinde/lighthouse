package com.midstane.lighthouse.repository.processor.mapper

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.midstane.lighthouse.repository.processor.crud.KotlinType

class MapperModelBuilder(
    private val logger: KSPLogger,
    private val functionBuilder: MapperFunctionModelBuilder = MapperFunctionModelBuilder(),
) {
    fun build(mapper: KSClassDeclaration): MapperModel? {
        if (mapper.classKind != ClassKind.INTERFACE) {
            logger.error("@Mapper can only be used on interfaces.", mapper)
            return null
        }

        val annotation = mapper.requireAnnotation(MAPPER) ?: return null
        val bindingScopeType = annotation.requireType("bindingScope", mapper) ?: return null
        val configuredName = annotation.stringOrDefault("generatedName")
        val declaredFunctions = mapper.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()
        val functions = declaredFunctions
            .mapNotNull { function -> function.toMapperFunction() }
            .toList()

        if (functions.isEmpty()) {
            if (declaredFunctions.isEmpty()) {
                logger.error("@Mapper interfaces must declare at least one mapping function.", mapper)
            }
            return null
        }

        return MapperModel(
            packageName = mapper.packageName.asString(),
            mapperName = mapper.simpleName.asString(),
            generatedName = configuredName.ifBlank { "Generated${mapper.simpleName.asString()}" },
            bindingScope = bindingScopeType.toKotlinType(),
            functions = functions,
            requiresExperimentalUuidOptIn = functions.any { it.usesExperimentalUuid() },
        )
    }

    private fun KSFunctionDeclaration.toMapperFunction(): MapperFunction? {
        if (Modifier.SUSPEND in modifiers) {
            logger.error("Mapper function '${simpleName.asString()}' must not be suspend.", this)
            return null
        }

        val sourceParameters = parameters.map { parameter ->
            val name = parameter.name?.asString() ?: run {
                logger.error("Mapper function '${simpleName.asString()}' parameters must be named.", parameter)
                return null
            }
            val type = parameter.type.resolve()
            val declaration = type.declaration as? KSClassDeclaration ?: run {
                logger.error("Mapper function '${simpleName.asString()}' parameter '$name' must be a class type.", parameter)
                return null
            }
            MapperSourceParameter(
                name = name,
                type = type.toKotlinType(),
                properties = declaration.toMapperProperties(),
            )
        }

        val returnType = returnType?.resolve() ?: run {
            logger.error("Mapper function '${simpleName.asString()}' must declare a return type.", this)
            return null
        }
        val target = returnType.declaration as? KSClassDeclaration ?: run {
            logger.error("Mapper function '${simpleName.asString()}' return type must be a class.", this)
            return null
        }
        if (target.classKind != ClassKind.CLASS || Modifier.ABSTRACT in target.modifiers) {
            logger.error("Mapper function '${simpleName.asString()}' return type must be a concrete class.", this)
            return null
        }
        val targetConstructor = target.primaryConstructor ?: run {
            logger.error("Mapper function '${simpleName.asString()}' return type must expose a primary constructor.", target)
            return null
        }

        val spec = MapperFunctionSpec(
            name = simpleName.asString(),
            parameters = sourceParameters,
            returnType = returnType.toKotlinType(),
            targetParameters = targetConstructor.parameters.mapNotNull { parameter ->
                val name = parameter.name?.asString() ?: return@mapNotNull null
                MapperTargetParameter(
                    name = name,
                    type = parameter.type.resolve().toKotlinType(),
                    hasDefault = parameter.hasDefault,
                )
            },
            mappings = mappingAnnotations(),
        )

        return when (val result = functionBuilder.build(spec)) {
            is MapperFunctionBuildResult.Success -> result.function
            is MapperFunctionBuildResult.Error -> {
                logger.error(result.message, this)
                null
            }
        }
    }

    private fun KSClassDeclaration.toMapperProperties(visited: Set<String> = emptySet()): List<MapperProperty> {
        val qualifiedName = qualifiedName?.asString() ?: return emptyList()
        if (!canInspectMapperProperties()) return emptyList()
        if (qualifiedName in visited) return emptyList()
        val nextVisited = visited + qualifiedName

        return getAllProperties().map { property ->
            val type = property.type.resolve()
            MapperProperty(
                name = property.simpleName.asString(),
                type = type.toKotlinType(),
                children = type.declaration.mapperChildren(nextVisited),
            )
        }.toList()
    }

    private fun KSDeclaration.mapperChildren(visited: Set<String>): List<MapperProperty> {
        val declaration = this as? KSClassDeclaration ?: return emptyList()
        declaration.qualifiedName?.asString() ?: return emptyList()
        return declaration.toMapperProperties(visited)
    }

    private fun KSClassDeclaration.canInspectMapperProperties(): Boolean {
        if (classKind != ClassKind.CLASS) return false
        val qualifiedName = qualifiedName?.asString() ?: return false
        return qualifiedName.platformPackagePrefix() == null
    }

    private fun String.platformPackagePrefix(): String? {
        return PLATFORM_PACKAGE_PREFIXES.firstOrNull { prefix -> startsWith(prefix) }
    }

    private fun KSFunctionDeclaration.mappingAnnotations(): List<RequestedMapping> {
        return annotations.flatMap { annotation ->
            when (annotation.annotationType.resolve().declaration.qualifiedName?.asString()) {
                MAPPING -> listOf(annotation.toRequestedMapping())
                MAPPINGS -> annotation.toRequestedMappings()
                else -> emptyList()
            }
        }.toList()
    }

    private fun KSAnnotation.toRequestedMappings(): List<RequestedMapping> {
        val values = arguments.firstOrNull { it.name?.asString() == "value" }?.value as? List<*>
        return values.orEmpty().filterIsInstance<KSAnnotation>().map { it.toRequestedMapping() }
    }

    private fun KSAnnotation.toRequestedMapping(): RequestedMapping {
        return RequestedMapping(
            target = stringOrDefault("target"),
            source = stringOrDefault("source"),
            expression = stringOrDefault("expression"),
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

    private fun KSAnnotation.requireType(name: String, target: KSAnnotated): KSType? {
        val value = arguments.firstOrNull { it.name?.asString() == name }?.value
        return value as? KSType ?: run {
            logger.error("Missing KClass annotation argument '$name'.", target)
            null
        }
    }

    private fun KSAnnotation.stringOrDefault(name: String): String {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? String ?: ""
    }

    private fun MapperFunction.usesExperimentalUuid(): Boolean {
        return returnType.usesExperimentalUuid() ||
            parameters.any { it.type.usesExperimentalUuid() } ||
            assignments.any { ".uuid" in it.expression || "Uuid" in it.expression }
    }

    private fun KotlinType.usesExperimentalUuid(): Boolean {
        return qualifiedName == "kotlin.uuid.Uuid" || arguments.any { it.usesExperimentalUuid() }
    }

    private fun KSClassDeclaration.requireAnnotation(qualifiedName: String): KSAnnotation? {
        return annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }
            ?: run {
                logger.error("Missing @$qualifiedName annotation.", this)
                null
            }
    }

    private companion object {
        const val MAPPER = "com.midstane.lighthouse.repository.annotations.Mapper"
        const val MAPPING = "com.midstane.lighthouse.repository.annotations.Mapping"
        const val MAPPINGS = "com.midstane.lighthouse.repository.annotations.Mappings"
        val PLATFORM_PACKAGE_PREFIXES = listOf(
            "kotlin.",
            "kotlinx.",
            "java.",
            "javax.",
        )
    }
}
