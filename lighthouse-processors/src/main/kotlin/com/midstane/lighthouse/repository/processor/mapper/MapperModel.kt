package com.midstane.lighthouse.repository.processor.mapper

import com.midstane.lighthouse.repository.processor.crud.KotlinType

data class MapperModel(
    val packageName: String,
    val mapperName: String,
    val generatedName: String,
    val bindingScope: KotlinType,
    val functions: List<MapperFunction>,
    val requiresExperimentalUuidOptIn: Boolean = false,
)

data class MapperFunction(
    val name: String,
    val parameters: List<MapperParameter>,
    val returnType: KotlinType,
    val assignments: List<PropertyAssignment>,
)

data class MapperParameter(
    val name: String,
    val type: KotlinType,
)

data class PropertyAssignment(
    val target: String,
    val expression: String,
)

data class MapperFunctionSpec(
    val name: String,
    val parameters: List<MapperSourceParameter>,
    val returnType: KotlinType,
    val targetParameters: List<MapperTargetParameter>,
    val mappings: List<RequestedMapping> = emptyList(),
)

data class MapperSourceParameter(
    val name: String,
    val type: KotlinType,
    val properties: List<MapperProperty>,
)

data class MapperTargetParameter(
    val name: String,
    val type: KotlinType,
    val hasDefault: Boolean = false,
)

data class MapperProperty(
    val name: String,
    val type: KotlinType,
    val children: List<MapperProperty> = emptyList(),
)

data class RequestedMapping(
    val target: String,
    val source: String = "",
    val expression: String = "",
)

sealed interface MapperFunctionBuildResult {
    data class Success(val function: MapperFunction) : MapperFunctionBuildResult
    data class Error(val message: String) : MapperFunctionBuildResult
}
