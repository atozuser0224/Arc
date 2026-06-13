plugins {
    kotlin("jvm") version "2.0.21"
}

group = "dev.arc.sample"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// arc-api ships as part of the Arc server, so everything below is compileOnly:
// the server already provides Bukkit/Paper, Adventure, Kotlin stdlib, and arc-api at runtime.
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly(files("../../arc-api/build/libs/arc-api-1.21.4-R0.1-SNAPSHOT.jar"))
    compileOnly(files("../../leaf-api/build/libs/leaf-api-1.21.4-R0.1-SNAPSHOT.jar"))
    compileOnly(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

tasks.jar {
    archiveBaseName.set("ArcShowcase")
    archiveVersion.set("")
    archiveClassifier.set("")
}
