package com.midstane.lighthouse.repository.processor.mapper

class MapperFunctionModelBuilder {
    fun build(spec: MapperFunctionSpec): MapperFunctionBuildResult {
        if (spec.parameters.isEmpty()) {
            return MapperFunctionBuildResult.Error("Mapper function '${spec.name}' must declare at least one source parameter.")
        }

        val mappingsByTarget = mutableMapOf<String, RequestedMapping>()
        spec.mappings.forEach { mapping ->
            if (mapping.target.isBlank()) {
                return MapperFunctionBuildResult.Error("Mapper function '${spec.name}' has a @Mapping with a blank target.")
            }
            if (mapping.source.isNotBlank() && mapping.expression.isNotBlank()) {
                return MapperFunctionBuildResult.Error(
                    "Mapper function '${spec.name}' mapping for '${mapping.target}' cannot set both source and expression.",
                )
            }
            if (mapping.source.isBlank() && mapping.expression.isBlank()) {
                return MapperFunctionBuildResult.Error(
                    "Mapper function '${spec.name}' mapping for '${mapping.target}' must set source or expression.",
                )
            }
            val previous = mappingsByTarget.put(mapping.target, mapping)
            if (previous != null) {
                return MapperFunctionBuildResult.Error(
                    "Mapper function '${spec.name}' declares multiple mappings for target '${mapping.target}'.",
                )
            }
        }

        val targetParameterNames = spec.targetParameters.map { it.name }.toSet()
        mappingsByTarget.keys.forEach { target ->
            if (target !in targetParameterNames) {
                return MapperFunctionBuildResult.Error(
                    "Mapper function '${spec.name}' maps unknown target constructor property '$target'.",
                )
            }
        }

        val assignments = spec.targetParameters.mapNotNull { target ->
            val requested = mappingsByTarget[target.name]
            if (requested == null) {
                when (val autoAssignment = spec.autoAssignment(target)) {
                    AutoAssignment.Default -> return@mapNotNull null
                    is AutoAssignment.Error -> return MapperFunctionBuildResult.Error(autoAssignment.message)
                    is AutoAssignment.Success -> return@mapNotNull autoAssignment.assignment
                }
            }

            if (requested.expression.isNotBlank()) {
                return@mapNotNull PropertyAssignment(target.name, requested.expression)
            }

            val source = spec.resolveSourcePath(requested.source)
                ?: return MapperFunctionBuildResult.Error(
                    "Mapper function '${spec.name}' mapping for '${target.name}' references unknown source '${requested.source}'.",
                )
            if (source.type != target.type) {
                return MapperFunctionBuildResult.Error(
                    "Mapper function '${spec.name}' mapping for '${target.name}' must be ${target.type.displayName}, but '${requested.source}' is ${source.type.displayName}.",
                )
            }
            PropertyAssignment(target.name, source.expression)
        }

        return MapperFunctionBuildResult.Success(
            MapperFunction(
                name = spec.name,
                parameters = spec.parameters.map { MapperParameter(it.name, it.type) },
                returnType = spec.returnType,
                assignments = assignments,
            ),
        )
    }

    private fun MapperFunctionSpec.autoAssignment(target: MapperTargetParameter): AutoAssignment {
        val candidates = parameters.mapNotNull { parameter ->
            when {
                parameter.name == target.name && parameter.type == target.type -> parameter.name
                else -> parameter.properties
                    .firstOrNull { property -> property.name == target.name && property.type == target.type }
                    ?.let { "${parameter.name}.${it.name}" }
            }
        }
        return when {
            candidates.size == 1 -> AutoAssignment.Success(PropertyAssignment(target.name, candidates.single()))
            candidates.size > 1 -> AutoAssignment.Error(
                "Mapper function '$name' target '${target.name}' is ambiguous across multiple source parameters. Add an explicit @Mapping.",
            )
            target.hasDefault -> AutoAssignment.Default
            else -> AutoAssignment.Error(
                "Mapper function '$name' cannot map target constructor property '${target.name}'. Add a matching source property, @Mapping source, @Mapping expression, or default value.",
            )
        }
    }

    private fun MapperFunctionSpec.resolveSourcePath(source: String): ResolvedSource? {
        val segments = source.split('.').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        val firstParameter = parameters.firstOrNull { it.name == segments.first() }
        if (firstParameter != null) {
            if (segments.size == 1) {
                return ResolvedSource(firstParameter.name, firstParameter.type)
            }
            return firstParameter.resolve(segments.drop(1), firstParameter.name)
        }

        val candidates = parameters.mapNotNull { parameter ->
            parameter.resolve(segments, parameter.name)
        }
        return candidates.singleOrNull()
    }

    private fun MapperSourceParameter.resolve(segments: List<String>, expressionPrefix: String): ResolvedSource? {
        var properties = properties
        lateinit var current: MapperProperty
        segments.forEach { segment ->
            current = properties.firstOrNull { it.name == segment } ?: return null
            properties = current.children
        }
        return ResolvedSource(
            expression = "$expressionPrefix.${segments.joinToString(".")}",
            type = current.type,
        )
    }

    private data class ResolvedSource(
        val expression: String,
        val type: com.midstane.lighthouse.repository.processor.crud.KotlinType,
    )

    private sealed interface AutoAssignment {
        data object Default : AutoAssignment
        data class Error(val message: String) : AutoAssignment
        data class Success(val assignment: PropertyAssignment) : AutoAssignment
    }
}
