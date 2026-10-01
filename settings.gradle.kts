pluginManagement {
    repositories {
        maven("https://maven.minecraftforge.net")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://maven.parchmentmc.org")
        mavenCentral()
        gradlePluginPortal()
        maven("https://plugins.gradle.org/m2/")
        maven("https://prmaven.neoforged.net/ModDevGradle/pr118") {
            name = "Maven for PR #118"
            content {
                includeModule("net.neoforged", "moddev-gradle")
                includeModule("net.neoforged.moddev", "net.neoforged.moddev.gradle.plugin")
                includeModule("net.neoforged.moddev.repositories", "net.neoforged.moddev.repositories.gradle.plugin")
                includeModule("net.neoforged.moddev.legacy", "net.neoforged.moddev.legacy.gradle.plugin")
            }
        }
    }

    // NeoForm (the Minecraft decompile pipeline driven by ModDevGradle) runs on a JDK 21
    // toolchain, which is not installed locally. The foojay resolver lets Gradle download
    // the required toolchain automatically instead of failing the build.
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }
}

val minecraft_version: String by settings
rootProject.name = "iron-${minecraft_version}"
