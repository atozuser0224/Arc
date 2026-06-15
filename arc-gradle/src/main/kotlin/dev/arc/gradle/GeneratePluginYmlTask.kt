package dev.arc.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class GeneratePluginYmlTask : DefaultTask() {

    @get:Input abstract val pluginName: Property<String>
    @get:Input abstract val pluginVersion: Property<String>
    @get:Input abstract val pluginMain: Property<String>
    @get:Input abstract val apiVersion: Property<String>
    @get:Input @get:Optional abstract val description: Property<String>
    @get:Input @get:Optional abstract val author: Property<String>
    @get:Input abstract val authors: ListProperty<String>
    @get:Input @get:Optional abstract val website: Property<String>
    @get:Input @get:Optional abstract val prefix: Property<String>
    @get:Input abstract val depend: ListProperty<String>
    @get:Input abstract val softDepend: ListProperty<String>
    @get:Input abstract val loadBefore: ListProperty<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val yml = buildString {
            appendLine("name: ${pluginName.get()}")
            appendLine("version: '${pluginVersion.get()}'")

            val main = pluginMain.get()
            require(main.isNotBlank()) {
                "arcPlugin { main } must be set (e.g. \"com.example.MyPlugin\")"
            }
            appendLine("main: $main")
            appendLine("api-version: '${apiVersion.get()}'")

            description.orNull?.takeIf { it.isNotBlank() }?.let { appendLine("description: $it") }

            val authorsList = authors.get()
            when {
                authorsList.size == 1 -> appendLine("author: ${authorsList[0]}")
                authorsList.size > 1 -> {
                    appendLine("authors:")
                    authorsList.forEach { appendLine("  - $it") }
                }
                author.orNull?.isNotBlank() == true -> appendLine("author: ${author.get()}")
            }

            website.orNull?.takeIf { it.isNotBlank() }?.let { appendLine("website: $it") }
            prefix.orNull?.takeIf { it.isNotBlank() }?.let { appendLine("prefix: $it") }

            depend.get().takeIf { it.isNotEmpty() }?.let { deps ->
                appendLine("depend:")
                deps.forEach { appendLine("  - $it") }
            }
            softDepend.get().takeIf { it.isNotEmpty() }?.let { deps ->
                appendLine("softdepend:")
                deps.forEach { appendLine("  - $it") }
            }
            loadBefore.get().takeIf { it.isNotEmpty() }?.let { deps ->
                appendLine("load-before:")
                deps.forEach { appendLine("  - $it") }
            }
        }

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(yml)
        }
    }
}
