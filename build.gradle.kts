plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "io.testsmith"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

intellij {
    version.set("2025.1")
    type.set("IC")
}

tasks {
    patchPluginXml {
        sinceBuild.set("251")
        untilBuild.set("251.*")
    }
}
