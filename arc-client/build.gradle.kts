plugins {
    id("fabric-loom") version "1.13.6"
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.4")
    mappings("net.fabricmc:yarn:1.21.4+build.8:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.10")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.119.4+1.21.4")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.12.3+kotlin.2.0.21")
    implementation(project(":arc-protocol"))
    include(project(":arc-protocol"))
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("arc") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}
