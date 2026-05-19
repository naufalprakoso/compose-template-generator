plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local("/Applications/Android Studio.app")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        pluginVerifier()
        zipSigner()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
    testRuntimeOnly("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.kmpfeaturekit"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }

        description = """
            Compose Template Generator accelerates Kotlin Multiplatform and Compose Multiplatform feature development with
            architecture-aware generation, source-set guardrails, inspections, quick fixes, project scans, and safe previews.
        """.trimIndent()

        changeNotes = """
            Initial preview release with architecture-aware generation, project scanning, inspections, settings, and documentation.
        """.trimIndent()
    }

    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }

    pluginVerification {
        ides {
            local("/Applications/Android Studio.app")
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild = providers.gradleProperty("pluginSinceBuild")
        untilBuild = providers.gradleProperty("pluginUntilBuild")
    }
}
