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
import com.midstane.lighthouse.repository.processor.crud.CrudRepositoryModelBuilder
import com.midstane.lighthouse.repository.processor.crud.ExposedCrudRepositoryRenderer

class CrudRepositoryProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return CrudRepositoryProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            modelBuilder = CrudRepositoryModelBuilder(environment.logger),
            renderer = ExposedCrudRepositoryRenderer(),
        )
    }
}

private class CrudRepositoryProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val modelBuilder: CrudRepositoryModelBuilder,
    private val renderer: ExposedCrudRepositoryRenderer,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation(DATA_REPOSITORY)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        symbols.filter { it.validate() }.forEach(::generateRepository)
        return symbols.filterNot { it.validate() }.toList()
    }

    private fun generateRepository(repository: KSClassDeclaration) {
        val model = modelBuilder.build(repository) ?: return
        val source = renderer.render(model)
        val sourceFile = repository.containingFile ?: run {
            logger.error("Could not determine the source file for ${repository.simpleName.asString()}.", repository)
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
        const val DATA_REPOSITORY =
            "com.midstane.lighthouse.repository.annotations.DataRepository"
    }
}
