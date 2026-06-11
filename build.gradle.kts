plugins {
    java
}

allprojects {
    group = "dev.invisiblespiders.haven"
    version = "1.0.0"

    repositories {
        mavenCentral()
        // Paper (also serves LuckPerms api)
        maven("https://repo.papermc.io/repository/maven-public/")
        // PlaceholderAPI
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        // VaultUnlockedAPI + LuckPerms snapshots
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/creatorfromhell/")
        // ExcellentEconomy
        maven("https://repo.nightexpressdev.com/releases")
        // JitPack fallback for misc deps
        maven("https://jitpack.io")
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
