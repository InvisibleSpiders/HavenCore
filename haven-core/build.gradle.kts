plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"
}

dependencies {
    implementation(project(":haven-api"))

    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")

    // Shaded into the final JAR.
    // Exclude slf4j — Paper already provides it on the runtime classpath.
    implementation("com.zaxxer:HikariCP:5.1.0") {
        exclude(group = "org.slf4j")
    }
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    runtimeOnly("com.mysql:mysql-connector-j:9.1.0")

    // Soft-depend — provided at runtime by server owners
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.15")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")

    // Paper API is compileOnly above; tests that touch Bukkit types need it too
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    testImplementation("net.milkbowl.vault:VaultUnlockedAPI:2.15")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.xerial:sqlite-jdbc:3.47.0.0")
    testImplementation("com.zaxxer:HikariCP:5.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

// Expand ${version} in plugin.yml at build time
tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("HavenCore-${project.version}-paper-26.1.2.jar")

    // Relocate HikariCP to avoid conflicts with other plugins.
    // sqlite-jdbc is intentionally NOT relocated: it resolves its native
    // library by package path, and relocating breaks native loading.
    relocate("com.zaxxer.hikari", "dev.invisiblespiders.haven.libs.hikari")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
