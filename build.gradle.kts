plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

group = "flipcards"
version = "1.0"

repositories { mavenCentral() }

kotlin {
    js {
        browser {
            commonWebpackConfig { outputFileName = "flipcards.js" }
            testTask {
                val chrome = providers.gradleProperty("testBrowser").orNull == "chrome"
                useKarma {
                    if (chrome) useChromeHeadless() else useFirefoxHeadless()
                }
                if (!chrome) {
                    // Firefox installed as a snap cannot read /tmp or hidden folders,
                    // so Karma's temporary profile has to live inside the project.
                    val firefoxTmp = layout.buildDirectory.dir("firefox-tmp").get().asFile
                    environment("TMPDIR", firefoxTmp.absolutePath)
                    doFirst { firefoxTmp.mkdirs() }
                }
            }
        }
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        jsMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-html:0.12.0")
        }
    }
}
