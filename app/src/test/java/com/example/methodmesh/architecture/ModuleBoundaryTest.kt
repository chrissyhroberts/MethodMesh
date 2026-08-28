package com.example.methodmesh.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModuleBoundaryTest {
    private val sourceRoot: File
        get() = listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull(File::isDirectory) ?: error("Cannot locate main source root")

    @Test
    fun `core and infrastructure import no concrete capability`() {
        val violations = sourceRoot.walkTopDown()
            .filter { it.extension == "kt" && "/modules/" !in it.invariantSeparatorsPath }
            .flatMap { file -> concreteModuleImports(file).map { "${file.path}: $it" } }
            .toList()
        assertTrue("Infrastructure imports concrete capabilities:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun `core imports no module layer`() {
        val coreRoot = File(sourceRoot, "com/example/methodmesh/core")
        val violations = coreRoot.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.useLines { lines ->
                lines.filter { it.trim().startsWith("import com.example.methodmesh.modules.") }
                    .map { "${file.path}: ${it.trim()}" }.toList().asSequence()
            }
        }.toList()
        assertTrue("Core depends on the module layer:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun `capability modules import no sibling capability implementation`() {
        val moduleRoot = File(sourceRoot, "com/example/methodmesh/modules")
        val violations = moduleRoot.listFiles().orEmpty()
            .filter(File::isDirectory)
            .flatMap { module ->
                module.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                    concreteModuleImports(file)
                        .filterNot { it.startsWith("com.example.methodmesh.modules.${module.name}.") }
                        .map { "${file.path}: $it" }
                }.toList()
            }
        assertTrue("Modules import sibling implementations:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun `parallel research model and central manifest are absent`() {
        assertFalse(File(sourceRoot, "com/example/methodmesh/core/research/ResearchRecords.kt").exists())
        assertFalse(File(sourceRoot, "com/example/methodmesh/modules/MethodMeshModuleManifest.kt").exists())
    }

    private fun concreteModuleImports(file: File): Sequence<String> = file.useLines { lines ->
        lines.mapNotNull { line ->
            Regex("^import (com\\.example\\.methodmesh\\.modules\\.[^.]+\\..+)$")
                .matchEntire(line.trim())?.groupValues?.get(1)
        }.toList().asSequence()
    }
}
