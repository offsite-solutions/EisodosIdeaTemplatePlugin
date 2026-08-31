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
}
