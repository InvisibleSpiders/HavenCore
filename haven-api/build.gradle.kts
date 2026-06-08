plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    // Paper API on compile classpath — implementors provide at runtime
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "haven-api"
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/InvisibleSpiders/HavenCore")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
