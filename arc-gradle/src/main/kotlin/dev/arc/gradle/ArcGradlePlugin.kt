package dev.arc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.ProcessResources

class ArcGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("arcPlugin", ArcPluginExtension::class.java, project)

        val generatedDir = project.layout.buildDirectory.dir("generated/resources/main")

        // ── plugin.yml generation ─────────────────────────────────────────────
        val generateTask = project.tasks.register("generatePluginYml", GeneratePluginYmlTask::class.java) { task ->
            task.group = "arc"
            task.description = "Generates plugin.yml from the arcPlugin { } DSL block"

            task.pluginName.set(project.provider { ext.name })
            task.pluginVersion.set(project.provider { ext.version })
            task.pluginMain.set(project.provider { ext.main })
            task.apiVersion.set(project.provider { ext.apiVersion })
            task.description.set(project.provider { ext.description })
            task.author.set(project.provider { ext.author })
            task.authors.set(project.provider { ext.authors })
            task.website.set(project.provider { ext.website })
            task.prefix.set(project.provider { ext.prefix })
            task.depend.set(project.provider { ext.depend })
            task.softDepend.set(project.provider { ext.softDepend })
            task.loadBefore.set(project.provider { ext.loadBefore })

            task.outputFile.set(generatedDir.map { it.file("plugin.yml") })
        }

        project.afterEvaluate {
            // Add generated resources dir to the main source set
            project.extensions.findByType(JavaPluginExtension::class.java)
                ?.sourceSets
                ?.findByName("main")
                ?.resources
                ?.srcDir(generatedDir)

            // ── Resource filtering ────────────────────────────────────────────
            // Replaces ${pluginVersion}, ${pluginName} in any .yml/.properties
            // resource file — useful for injecting the version into config.yml.
            project.tasks.withType(ProcessResources::class.java).configureEach { task ->
                val props = mapOf(
                    "pluginVersion" to ext.version,
                    "pluginName" to ext.name,
                )
                task.inputs.properties(props)
                task.filesMatching(listOf("**/*.yml", "**/*.properties")) { details ->
                    // Skip the generated plugin.yml — it already has the correct values
                    if (details.name != "plugin.yml") {
                        details.expand(props)
                    }
                }
            }

            // ── Paper API auto-dependency ─────────────────────────────────────
            // Adds compileOnly paper-api automatically so the plugin's build.gradle.kts
            // doesn't need a manual dependency declaration for it.
            val mcVersion = when {
                ext.apiVersion.startsWith("1.21") -> "1.21.4"
                ext.apiVersion.startsWith("1.20") -> "1.20.6"
                else -> "${ext.apiVersion}.0"
            }
            project.configurations.findByName("compileOnly")?.let { config ->
                project.dependencies.add(
                    config.name,
                    "io.papermc.paper:paper-api:$mcVersion-R0.1-SNAPSHOT"
                )
            }
        }

        project.tasks.named("processResources") { it.dependsOn(generateTask) }

        // ── deployToServer task ───────────────────────────────────────────────
        project.tasks.register("deployToServer", DeployToServerTask::class.java) { task ->
            task.group = "arc"
            task.description = "Copies the plugin jar into the configured local server's plugins/ folder"
            task.serverPluginsDir.set(project.provider { ext.serverDir })

            // Wire to the jar task output — works with both plain `jar` and shadow jars
            val jarTask = project.tasks.findByName("shadowJar") ?: project.tasks.getByName("jar")
            task.dependsOn(jarTask)
            task.jarFile.set(
                project.layout.file(project.provider { jarTask.outputs.files.singleFile })
            )
        }
    }
}
