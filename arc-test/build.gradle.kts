// Arc start - arc-test module
// Server-free testing framework for arc-api plugins.
// Provides VirtualPlayer, TickClock, multi-server VirtualNetwork, in-memory DB,
// and extra tooling: EventTimeline, WorldSnapshot, FuzzScope, BenchmarkScope, etc.

dependencies {
    // Arc API surface under test
    api(project(":arc-api"))

    // MockBukkit — Paper 1.21 stubs (implements Bukkit/Paper API interfaces in pure JVM)
    // https://github.com/MockBukkit/MockBukkit — update version as new releases land
    api("com.github.MockBukkit:MockBukkit:v4.31.1") {
        exclude(group = "io.papermc.paper", module = "paper-api")
    }

    // Coroutine test utilities (runTest, advanceTimeBy, TestCoroutineScheduler)
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // JUnit 5 (re-exported so consumers don't need to add it themselves)
    api(kotlin("test-junit5"))
    api("org.junit.jupiter:junit-jupiter:5.10.2")

    // Gson — ScenarioRecorder JSON serialization
    api("com.google.code.gson:gson:2.11.0")

    // SQLite JDBC driver — in-memory DB for TestDatabase
    api("org.xerial:sqlite-jdbc:3.47.1.0")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") { name = "jitpack" }
}

tasks.test {
    useJUnitPlatform()
}
// Arc end - arc-test module
