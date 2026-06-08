plugins {
    java
}

allprojects {
    group = "dev.invisiblespiders.haven"
    version = "1.0.0"

    repositories {
        mavenCentral()
        // Paper — verify snapshot string against https://repo.papermc.io for 1.26.1
        maven("https://repo.papermc.io/repository/maven-public/")
        // PlaceholderAPI
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        // VaultUnlocked — replace with official repo once published; JitPack as fallback
        maven("https://jitpack.io")
        // LuckPerms
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
