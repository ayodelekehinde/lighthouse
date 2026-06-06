package com.midstane.lighthouse.repository.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.midstane.lighthouse.repository.processor.mapper.MapperModelBuilder
import com.midstane.lighthouse.repository.processor.mapper.MapperRenderer

class MapperProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return MapperProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            modelBuilder = MapperModelBuilder(environment.logger),
            renderer = MapperRenderer(),
        )
    }
}

private class MapperProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val modelBuilder: MapperModelBuilder,
    private val renderer: MapperRenderer,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation(MAPPER)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        symbols.filter { it.validate() }.forEach(::generateMapper)
        return symbols.filterNot { it.validate() }.toList()
    }

    private fun generateMapper(mapper: KSClassDeclaration) {
        val model = modelBuilder.build(mapper) ?: return
        val source = renderer.render(model)
        val sourceFile = mapper.containingFile ?: run {
            logger.error("Could not determine the source file for ${mapper.simpleName.asString()}.", mapper)
            return
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, sourceFile),
            packageName = model.packageName,
            fileName = model.generatedName,
        ).writer().use { writer ->
            writer.write(source)
        }
    }

    private companion object {
        const val MAPPER = "com.midstane.lighthouse.repository.annotations.Mapper"
    }
}
