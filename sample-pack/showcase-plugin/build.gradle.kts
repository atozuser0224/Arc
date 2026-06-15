plugins {
    kotlin("jvm") version "2.0.21"
    id("dev.arc.plugin")
}

group = "dev.arc.sample"
version = "1.0.0"

arcPlugin {
    name = "ArcShowcase"
    main = "dev.arc.sample.ArcShowcasePlugin"
    apiVersion = "1.21"
    author = "Arc"
    description = "A guided tour of the Arc developer API (effects, PDC, datapacks, item auth, events, command DSL)."
    // 종속 플러그인 선언 예시 — plugin.yml 건드릴 필요 없음
    // softDepend("Vault", "PlaceholderAPI")
    // serverDir = "/path/to/server/plugins"  // enables ./gradlew deployToServer
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // paper-api is added automatically by arc-gradle based on arcPlugin { apiVersion }
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
