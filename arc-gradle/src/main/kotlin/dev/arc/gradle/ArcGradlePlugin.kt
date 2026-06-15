package dev.arc.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

class ArcGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("arcPlugin", ArcPluginExtension::class.java, project)

        val generatedDir = project.layout.buildDirectory.dir("generated/resources/main")

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

        project.plugins.withType(JavaPluginExtension::class.java) {}
        project.afterEvaluate {
            project.extensions.findByType(JavaPluginExtension::class.java)
                ?.sourceSets
                ?.findByName("main")
                ?.resources
                ?.srcDir(generatedDir)
        }

        project.tasks.named("processResources") { it.dependsOn(generateTask) }
    }
}
