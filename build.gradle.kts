plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "smith.testsmith"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        create("IC", "2025.1")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "251"; untilBuild = "252.*" }
    }
}
