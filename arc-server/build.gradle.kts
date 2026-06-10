// Arc start - arc-server module
// Server-side implementation of arc-api. NMS-only capabilities are bridged via
// reflection (see dev.arc.server.nms), so this module does NOT take a compile
// dependency on the obfuscation-volatile server internals and keeps building
// across Minecraft/mapping bumps.

dependencies {
    api(project(":arc-api"))
    compileOnly("io.netty:netty-transport:4.1.115.Final")
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
// Arc end - arc-server module
