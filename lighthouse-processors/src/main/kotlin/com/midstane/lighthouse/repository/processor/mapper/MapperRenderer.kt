package com.midstane.lighthouse.repository.processor.mapper

class MapperRenderer {
    fun render(model: MapperModel): String = buildString {
        if (model.usesExperimentalUuid() || model.requiresExperimentalUuidOptIn) {
            appendLine("@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)")
            appendLine()
        }
        appendLine("package ${model.packageName}")
        appendLine()
        appendLine("import dev.zacsweers.metro.ContributesBinding")
        appendLine("import dev.zacsweers.metro.Inject")
        appendLine("import dev.zacsweers.metro.binding")
        appendLine()
        appendLine("@ContributesBinding(${model.bindingScope.qualifiedName}::class, binding<${model.mapperName}>())")
        appendLine("@Inject")
        appendLine("class ${model.generatedName} : ${model.mapperName} {")
        model.functions.forEachIndexed { index, function ->
            if (index > 0) appendLine()
            appendFunction(function)
        }
        appendLine("}")
    }

    private fun StringBuilder.appendFunction(function: MapperFunction) {
        appendLine("    override fun ${function.name}(${function.renderParameters()}): ${function.returnType.render()} {")
        appendLine("        return ${function.returnType.render()}(")
        function.assignments.forEach { assignment ->
            appendLine("            ${assignment.target} = ${assignment.expression},")
        }
        appendLine("        )")
        appendLine("    }")
    }

    private fun MapperFunction.renderParameters(): String {
        return parameters.joinToString(", ") { parameter ->
            "${parameter.name}: ${parameter.type.render()}"
        }
    }

    private fun MapperModel.usesExperimentalUuid(): Boolean {
        return functions.any { function ->
            function.returnType.usesExperimentalUuid() ||
                function.parameters.any { it.type.usesExperimentalUuid() }
        }
    }

    private fun com.midstane.lighthouse.repository.processor.crud.KotlinType.usesExperimentalUuid(): Boolean {
        return qualifiedName == "kotlin.uuid.Uuid" || arguments.any { it.usesExperimentalUuid() }
    }

    private fun com.midstane.lighthouse.repository.processor.crud.KotlinType.render(): String = displayName
}
