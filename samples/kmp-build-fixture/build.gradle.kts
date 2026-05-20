plugins {
    kotlin("multiplatform") version "2.2.21"
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
