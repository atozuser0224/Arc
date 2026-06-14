// Arc start - arc-api module
// Pure-Kotlin developer-facing API layer on top of the Bukkit/Paper/Leaf API.
// Plugins (java-library, maven-publish, kotlin.jvm) and Kotlin compiler options
// are applied to every subproject from the root build.gradle.kts.

dependencies {
    api(project(":arc-protocol"))
    // Re-expose the full Bukkit/Paper API (incl. Adventure) to Arc API consumers.
    api(project(":leaf-api"))
    // Coroutine support for the Bukkit-main dispatcher / structured concurrency DSL.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
// Arc end - arc-api module
