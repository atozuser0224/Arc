package dev.arc.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

/**
 * Copies the plugin jar directly into a local test server's plugins/ folder.
 *
 * Configure the server directory once in arcPlugin { serverDir = "..." } and run:
 *   ./gradlew deployToServer
 *
 * The task depends on `jar` automatically so the jar is always up to date before copying.
 */
abstract class DeployToServerTask : DefaultTask() {

    @get:InputFile
    abstract val jarFile: RegularFileProperty

    @get:Input
    abstract val serverPluginsDir: org.gradle.api.provider.Property<String>

    @TaskAction
    fun deploy() {
        val dir = serverPluginsDir.get()
        require(dir.isNotBlank()) {
            "arcPlugin { serverDir } is not set. " +
            "Point it to your test server's plugins/ folder, e.g. serverDir = \"/srv/mc/plugins\""
        }

        val target = project.file(dir)
        require(target.exists() && target.isDirectory) {
            "serverDir '${target.absolutePath}' does not exist or is not a directory"
        }

        val jar = jarFile.get().asFile
        val dest = target.resolve(jar.name)
        jar.copyTo(dest, overwrite = true)
        logger.lifecycle("Deployed ${jar.name} → ${dest.absolutePath}")
    }
}
