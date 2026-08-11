import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kobweb.application)
    alias(libs.plugins.kobwebx.markdown)
}

group = "org.example.app"
version = "1.0-SNAPSHOT"

kobweb {
    app {
        index {
            description.set("Powered by Kobweb")
        }
    }
}

kotlin {
    // Enable the server target here:
    configAsKobwebApplication("app", includeServer = true)

    sourceSets {
        jsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.html.core)
            implementation(libs.kobweb.core)
            implementation(libs.kobweb.silk)
            implementation(libs.kobwebx.markdown)
            implementation(libs.kotlinx.serialization.json)
        }

        // Uncomment this block so backend code (`jvmMain`) is recognized:
        jvmMain.dependencies {
            implementation(libs.kobweb.api)
            implementation(libs.kobwebx.serialization.kotlinx)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}