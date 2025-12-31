plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    google()
    mavenCentral()
}

group = "cc.unitmesh"
version = project.findProperty("mppVersion") as String? ?: "0.1.5"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    js(IR) {
        outputModuleName = "xiuper-e2e"
        browser()
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()

        compilerOptions {
            moduleKind.set(org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_UMD)
            sourceMap.set(true)
        }
    }

    // iOS targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":mpp-core"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            dependencies {
                // Ktor for LLM integration
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        jsMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

// Task to run DSL generation with LLM
tasks.register<JavaExec>("generateE2EDslCases") {
    group = "verification"
    description = "Generate E2E DSL test cases using LLM"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.e2e.dsl.GenerateDslCasesKt")
}

