plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    alias(libs.plugins.sqldelight)
}

group = "cc.unitmesh"
version = project.findProperty("mppVersion") as String? ?: "0.1.5"

repositories {
    google()
    mavenCentral()
}

// Force consistent Kotlin stdlib version across all dependencies
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.2.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
        force("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
    }
}

sqldelight {
    databases {
        create("XiuperFsDatabase") {
            packageName.set("cc.unitmesh.xiuper.fs.db")
        }
    }
}

android {
    namespace = "cc.unitmesh.xiuper.fs"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm()

    js(IR) {
        nodejs()
        binaries.library()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "XiuperFs"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelight.sqlite)
                // MCP SDK for JVM only (iOS not supported yet)
                implementation(libs.mcp.kotlin.sdk)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.sqldelight.android)
                // MCP SDK for Android
                implementation(libs.mcp.kotlin.sdk)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
                // MCP SDK for JS
                implementation(libs.mcp.kotlin.sdk)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
                // SQLDelight web worker driver for browser WASM usage.
                implementation(libs.sqldelight.webWorker)
                implementation(libs.sqldelight.webWorker.wasmJs)
                implementation(npm("sql.js", "1.8.0"))
                implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.1.0"))
                // MCP SDK for WASM
                implementation(libs.mcp.kotlin.sdk)
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.native)
                // Note: MCP SDK doesn't support iOS targets yet
                // TODO: Add MCP support when available for iOS
            }
        }
    }
}
