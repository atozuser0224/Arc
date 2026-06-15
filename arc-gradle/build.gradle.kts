plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "dev.arc"
version = "1.21.4-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("arcPlugin") {
            id = "dev.arc.plugin"
            implementationClass = "dev.arc.gradle.ArcGradlePlugin"
            displayName = "Arc Plugin"
            description = "Generates plugin.yml from a type-safe Kotlin DSL"
        }
    }
}

kotlin {
    jvmToolchain(21)
}
