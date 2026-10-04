plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

group = "dev.silas"
version = "1.0"

repositories { mavenCentral() }

kotlin {
    js {
        browser {
            commonWebpackConfig { outputFileName = "flipcards.js" }
            testTask {
                // The same browser locally and on CI: choosing another one changes the npm
                // dependencies, and the build then fails because kotlin-js-store/yarn.lock no longer matches.
                useKarma { useFirefoxHeadless() }
                // Firefox installed as a snap cannot read /tmp or hidden folders,
                // so Karma's temporary profile has to live inside the project.
                val firefoxTmp = layout.buildDirectory.dir("firefox-tmp").get().asFile
                environment("TMPDIR", firefoxTmp.absolutePath)
                doFirst { firefoxTmp.mkdirs() }
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
