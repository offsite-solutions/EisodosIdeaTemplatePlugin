import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion"),
        )
        pluginVerifier()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    buildSearchableOptions = false

    pluginVerification {
        // The plugin ID contains the word "intellij", which the Marketplace naming policy flags.
        // It is not an IDE-compatibility problem, and renaming the ID would orphan existing
        // installations, so the check is muted.
        freeArgs = listOf("-mute", "TemplateWordInPluginId")

        ides {
            providers.gradleProperty("pluginVerifierIdeVersions").get()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach { create(IntelliJPlatformType.PhpStorm, it) }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "9.7.1"
    }

    // Writes updatePlugins.xml, the custom plugin repository PhpStorm polls for updates.
    // Its download URL points to the GitHub Release asset of the current version, so
    // publish the release (tag v<pluginVersion>) together with the regenerated file.
    register("updatePluginsXml") {
        group = "intellij platform"
        description = "Generates updatePlugins.xml for the custom plugin repository."

        val pluginVersion = providers.gradleProperty("pluginVersion")
        val sinceBuild = providers.gradleProperty("pluginSinceBuild")
        val untilBuild = providers.gradleProperty("pluginUntilBuild")
        val pluginName = providers.gradleProperty("pluginName")
        val outputFile = layout.projectDirectory.file("updatePlugins.xml")
        outputs.file(outputFile)

        doLast {
            val version = pluginVersion.get()
            val url = "https://github.com/offsite-solutions/EisodosIdeaTemplatePlugin" +
                "/releases/download/v$version/eisodos-template-support-$version.zip"
            outputFile.asFile.writeText(
                """
                |<?xml version="1.0" encoding="UTF-8"?>
                |<plugins>
                |  <plugin id="com.eisodos.intellij.template" url="$url" version="$version">
                |    <idea-version since-build="${sinceBuild.get()}" until-build="${untilBuild.get()}"/>
                |    <name>${pluginName.get()}</name>
                |    <vendor>Offsite Solutions Kft.</vendor>
                |  </plugin>
                |</plugins>
                |""".trimMargin()
            )
        }
    }
}
