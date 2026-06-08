plugins {
    java
    id("io.github.goooler.shadow") version "8.1.8"
}

dependencies {
    implementation(project(":haven-api"))

    compileOnly("io.papermc.paper:paper-api:1.26.1-R0.1-SNAPSHOT")

    // Shaded into the final JAR
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.22.0")
    implementation("org.flywaydb:flyway-database-sqlite:10.22.0")
    implementation("org.flywaydb:flyway-mysql:10.22.0")
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    runtimeOnly("com.mysql:mysql-connector-j:9.1.0")

    // Soft-depend — provided at runtime by server owners
    compileOnly("com.github.MilkBowl:VaultUnlocked:master-SNAPSHOT") // verify coords against VaultUnlocked repo
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.xerial:sqlite-jdbc:3.47.0.0")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("HavenAPI-${project.version}-paper-26.1.2.jar")

    // Relocate shaded libs to avoid conflicts with other plugins
    relocate("com.zaxxer.hikari", "dev.invisiblespiders.haven.libs.hikari")
    relocate("org.flywaydb", "dev.invisiblespiders.haven.libs.flyway")
    relocate("org.sqlite", "dev.invisiblespiders.haven.libs.sqlite")
    relocate("org.slf4j", "dev.invisiblespiders.haven.libs.slf4j")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
