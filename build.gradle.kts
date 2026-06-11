plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

val androidStudioPath = providers.gradleProperty("androidStudioPath")
    .orElse(providers.environmentVariable("ANDROID_STUDIO_PATH"))
    .orElse("/Applications/Android Studio.app")
    .get()

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
        local(androidStudioPath)
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        pluginVerifier()
        zipSigner()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
    testImplementation(kotlin("compiler-embeddable"))
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
            Compose Template Generator creates Kotlin Multiplatform / Compose feature files from Android Studio or IntelliJ IDEA.
            It previews generated and modified files before writing them, can wire supported DI/navigation patterns into recognized project files, and includes inspections for common KMP source-set mistakes.
        """.trimIndent()

        changeNotes = """
            Adds layered-global MVVM project detection and generation for projects that organize code under data/domain/presentation/ui, improves custom-navigation defaults, patches manual AppGraph roots more naturally, and makes change-preview popups scrollable.
        """.trimIndent()
    }

    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }

    pluginVerification {
        ides {
            local(androidStudioPath)
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
