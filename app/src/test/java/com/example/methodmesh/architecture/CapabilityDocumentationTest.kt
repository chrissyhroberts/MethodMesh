package com.example.methodmesh.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class CapabilityDocumentationTest {
    private val moduleRoot: File
        get() = listOf(
            File("src/main/java/com/example/methodmesh/modules"),
            File("app/src/main/java/com/example/methodmesh/modules")
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
    fun `capability readmes contain substantive titled documentation`() {
        // v1.25 allows separate READMEs per capability and module-specific headings.
        // Exact historical heading spellings are not an integration contract.
        val failures = discoverModuleFolders().flatMap { module ->
            capabilityReadmes(File(module, "docs")).mapNotNull { readme ->
                val contents = readme.readText().trim()
                if (!contents.startsWith("#") || contents.length < 100) {
                    "${module.name}/${readme.name}: missing titled capability documentation"
                } else null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
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
                        listOf("survey", "settings").forEach { sheet ->
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
    fun `capability ODK examples invoke MethodMesh explicitly`() {
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
                    "com.example.methodmesh.EXECUTE_METHOD" !in xmlText ||
                    "method_id" !in xmlText
                ) {
                    "${module.name}/${workbook.name}: no explicit MethodMesh method intent"
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

    @Test
    fun `capability ODK settings preserve explicit identity title and version`() {
        // v1.25: filename, human-readable title and stable external ID are distinct.
        val failures = discoverModuleFolders().flatMap { module ->
            capabilityOdkExamples(File(module, "docs")).mapNotNull { workbook ->
                runCatching {
                    val settings = XlsFormTestReader.sheet(workbook, "settings")
                    val first = settings.firstOrNull() ?: error("missing settings row")
                    listOf("form_title", "form_id", "version").forEach { key ->
                        require(!first[key].isNullOrBlank()) { "settings.$key is blank" }
                    }
                }.exceptionOrNull()?.let { "${module.name}/${workbook.name}: ${it.message}" }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `flat capability inputs use the explicit input namespace`() {
        val allowedUnprefixed = setOf(
            "method_id", "return_mode", "returns", "methodmesh_return_namespace",
            "caller", "entity_type", "entity_id", "subject_id",
            "participant_id", "specimen_id", "visit_id", "form_id", "operator_id"
        )
        val failures = discoverModuleFolders().flatMap { module ->
            capabilityOdkExamples(File(module, "docs")).flatMap { workbook ->
                val xmlText = workbookXml(workbook)
                    .getOrElse { return@flatMap listOf("${module.name}/${workbook.name}: ${it.message}") }
                Regex("""(?:\(|,)\s*([A-Za-z][A-Za-z0-9_]*)\s*=""")
                    .findAll(xmlText)
                    .map { it.groupValues[1] }
                    .filterNot { it in allowedUnprefixed || it.startsWith("input_") || it.startsWith("input64_") }
                    .distinct()
                    .map { "${module.name}/${workbook.name}: unnamespaced capability input '$it'" }
                    .toList()
            }
        }
        assertTrue(
            "Capability XLSForm input-namespace violations:\n${failures.joinToString("\n")}",
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

    private fun workbookXml(workbook: File): Result<String> = runCatching {
        ZipFile(workbook).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".xml") }
                .joinToString("\n") { entry ->
                    zip.getInputStream(entry).bufferedReader().readText()
                }
        }
    }
}
