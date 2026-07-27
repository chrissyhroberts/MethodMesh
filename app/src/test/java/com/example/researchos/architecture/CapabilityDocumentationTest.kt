package com.example.researchos.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class CapabilityDocumentationTest {
    private val moduleRoot: File
        get() = listOf(
            File("src/main/java/com/example/researchos/modules"),
            File("app/src/main/java/com/example/researchos/modules")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate capability module root")

    private val requiredReadmeSections = listOf(
        "## Capabilities",
        "## Android intent",
        "## Inputs",
        "## Outputs",
        "## ODK example"
    )

    @Test
    fun `every standalone capability module owns documentation and an ODK example`() {
        val failures = discoverModuleFolders().flatMap { module ->
            val docs = File(module, "docs")
            val readmes = capabilityReadmes(docs)
            val xlsForms = capabilityOdkExamples(docs)
            buildList {
                if (readmes.isEmpty()) {
                    add("${module.name}: missing docs/README_<CapabilityModule>.md")
                } else if (readmes.size > 1) {
                    add("${module.name}: expected one capability README, found ${readmes.size}")
                }
                if (xlsForms.isEmpty()) {
                    add("${module.name}: missing docs/example_odk_<Capability>.xlsx")
                }
            }
        }
        assertTrue(
            "Capability documentation contract violations:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    @Test
    fun `capability readmes contain the required implementation sections`() {
        val failures = discoverModuleFolders().flatMap { module ->
            val readme = capabilityReadmes(File(module, "docs")).singleOrNull()
                ?: return@flatMap emptyList()
            val contents = readme.readText()
            requiredReadmeSections
                .filterNot(contents::contains)
                .map { "${module.name}: README missing '$it'" }
        }
        assertTrue(
            "Capability README contract violations:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    @Test
    fun `capability ODK examples are real XLSForm workbooks`() {
        val failures = discoverModuleFolders().flatMap { module ->
            capabilityOdkExamples(File(module, "docs")).mapNotNull { workbook ->
                runCatching {
                    ZipFile(workbook).use { zip ->
                        val workbookXml = zip.getInputStream(
                            zip.getEntry("xl/workbook.xml")
                                ?: error("xl/workbook.xml is absent")
                        ).bufferedReader().readText()
                        listOf("survey", "choices", "settings").forEach { sheet ->
                            require(Regex("""name="$sheet"""", RegexOption.IGNORE_CASE).containsMatchIn(workbookXml)) {
                                "missing '$sheet' sheet"
                            }
                        }
                    }
                }.exceptionOrNull()?.let { "${module.name}/${workbook.name}: ${it.message}" }
            }
        }
        assertTrue(
            "Capability XLSForm contract violations:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    @Test
    fun `capability ODK examples invoke ResearchOS explicitly`() {
        val failures = discoverModuleFolders().flatMap { module ->
            capabilityOdkExamples(File(module, "docs")).mapNotNull { workbook ->
                val xmlText = runCatching {
                    ZipFile(workbook).use { zip ->
                        zip.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".xml") }
                            .joinToString("\n") { entry ->
                                zip.getInputStream(entry).bufferedReader().readText()
                            }
                    }
                }.getOrElse { return@mapNotNull "${module.name}/${workbook.name}: ${it.message}" }

                if (
                    "com.example.researchos.EXECUTE_METHOD" !in xmlText ||
                    "method_id" !in xmlText
                ) {
                    "${module.name}/${workbook.name}: no explicit ResearchOS method intent"
                } else {
                    null
                }
            }
        }
        assertTrue(
            "Capability XLSForm invocation violations:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    private fun discoverModuleFolders(): List<File> = moduleRoot.listFiles().orEmpty()
        .filter(File::isDirectory)
        .filter { module -> module.listFiles().orEmpty().any { it.name.endsWith("Module.kt") } }
        .sortedBy(File::getName)

    private fun capabilityReadmes(docs: File): List<File> = docs.listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("README_") && it.extension == "md" }
        .sortedBy(File::getName)

    private fun capabilityOdkExamples(docs: File): List<File> = docs.listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("example_odk_") && it.extension == "xlsx" }
        .sortedBy(File::getName)
}
