plugins {
    java
}

group = "com.additionalbosses"
// Builds from GitHub Actions carry their run number and commit ("1.0.0-b57 (3fc9923)"), shown by
// /version <plugin> and on startup, so it's easy to see which build a server is running.
val ciRun: String? = System.getenv("GITHUB_RUN_NUMBER")
val ciSha: String? = System.getenv("GITHUB_SHA")?.take(7)
version = if (ciRun != null) "1.0.0-b$ciRun ($ciSha)" else "1.0.0-dev"
description = "Ordinary mobs occasionally become ranked bosses with traits, bossbars and special rewards."

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API for Minecraft 26.3 (the "+" picks the newest 26.3 build).
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}

java {
    // Minecraft 26.x / Paper 26.x require Java 25.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("AdditionalBosses")
    archiveVersion.set("") // always the same file name, so a new download replaces the old jar
}
