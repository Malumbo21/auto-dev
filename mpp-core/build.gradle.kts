import org.gradle.kotlin.dsl.implementation

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    `maven-publish`

    // Temporarily disabled: npm publish plugin doesn't support wasmJs targets
    // TODO: Re-enable once plugin supports wasmJs or split into separate modules
    // id("dev.petuska.npm.publish") version "3.5.3"
}

repositories {
    google()
    mavenCentral()
}

group = "cc.unitmesh"
version = project.findProperty("mppVersion") as String? ?: "0.1.5"

android {
    namespace = "cc.unitmesh.devins.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
        
        iosTarget.binaries.framework {
            baseName = "AutoDevCore"
            isStatic = true

            // Export coroutines for Swift interop
            export("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }

        // Configure cinterop for Swift MCP bridge
        // Note: This requires the Swift bridge to be compiled first
        // In practice, this may need to be handled by CocoaPods
        /*
        iosTarget.compilations.getByName("main") {
            cinterops {
                val mcpBridge by creating {
                    defFile(project.file("src/iosMain/cinterop/mcpBridge.def"))
                    packageName("cc.unitmesh.agent.mcp.bridge")
                    includeDirs(project.file("src/iosMain/swift"))
                }
            }
        }
        */
    }

    js(IR) {
        outputModuleName = "xiuper-mpp-core"
        // Support both browser and Node.js with UMD (for compatibility)
        browser()
        nodejs()
        binaries.library()
        // Generate TypeScript definitions for better interop
        generateTypeScriptDefinitions()

        compilerOptions {
            // UMD is the most compatible format for both Node.js and browser (with bundlers)
            moduleKind.set(org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_UMD)
            sourceMap.set(true)
            sourceMapEmbedSources.set(org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS)
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
        binaries.library()
        // Use d8 optimizer instead of binaryen to avoid wasm-validator errors
        d8 {
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // MPP Linter dependency
                implementation(project(":mpp-codegraph"))

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kaml)
                // kotlinx-io for cross-platform file system operations
                implementation(libs.kotlinx.io.core)

                // JetBrains Markdown parser for document parsing
                implementation(libs.markdown)

                // Ktor HTTP Client for web fetching (core only in common)
                implementation(libs.ktor.client.core)

                // Kotlin Logging for multiplatform logging
                implementation(libs.kotlin.logging)

                implementation(libs.kotlinx.datetime)
                // Koog AI Framework - JVM only for now
                implementation(libs.koog.agents)
                implementation(libs.koog.agents.mcp)
                // Koog needs these executors
                implementation(libs.koog.prompt.executor)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        androidMain {
            dependencies {
                // AndroidX DocumentFile for SAF support
                implementation(libs.androidx.documentfile)

                // Ktor CIO engine for Android
                implementation(libs.ktor.client.cio)
            }
        }

        jvmMain {
            repositories {
                google()
                mavenCentral()
                maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
            }

            dependencies {
                // ACP (Agent Client Protocol) - JVM-only (no Kotlin/Native variants)
                implementation(libs.acp.sdk)
                implementation(libs.acp.model)

                // Ktor CIO engine for JVM
                implementation(libs.ktor.client.cio)
                // Ktor content negotiation - required by ai.koog:prompt-executor-llms-all
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                // CodeGraph for source code parsing
                implementation(project(":mpp-codegraph"))

                // Logback for JVM logging backend with file storage
                implementation(libs.logback)

                // JediTerm for terminal emulation (uses pty4j under the hood)
                implementation(libs.pty4j)

                // Apache Tika for document parsing (PDF, DOC, DOCX, PPT, etc.)
                implementation(libs.tika.core)
                implementation(libs.tika.parsers)
                
                // Jsoup for HTML document parsing
                implementation(libs.jsoup)

                // PDFBox for PDF document parsing
                implementation(libs.pdfbox.get().toString()) {
                    exclude(group = "commons-logging", module = "commons-logging")
                }

                // JetBrains Exposed - SQL framework for database access
                implementation(libs.exposed.core)
                implementation(libs.exposed.dao)
                implementation(libs.exposed.jdbc)
                
                // MySQL/MariaDB JDBC Driver
                implementation(libs.mysql.connector)
                
                // Connection pooling
                implementation(libs.hikari)
                
                // JSQLParser for SQL validation and parsing
                implementation(libs.jsqlparser)

                // MyNLP for Chinese NLP tokenization
                implementation(libs.mynlp)
                implementation(libs.mynlp.all)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit"))
                // H2 database for testing
                implementation(libs.h2)
            }
        }

        jsMain {
            dependencies {
                // Ktor JS engine for JavaScript
                implementation(libs.ktor.client.js)
                
                // CodeGraph for source code parsing
                implementation(project(":mpp-codegraph"))
                
                // web-tree-sitter for source code parsing (required for JsCodeParser)
                implementation(npm("web-tree-sitter", "0.22.2"))
                implementation(npm("@unit-mesh/treesitter-artifacts", "1.7.7"))
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        iosMain {
            dependencies {
                // Ktor Darwin engine for iOS
                implementation(libs.ktor.client.darwin)

                // Export coroutines for Swift interop (required by framework export)
                api(libs.kotlinx.coroutines.core)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(devNpm("copy-webpack-plugin", "12.0.2"))

                implementation(npm("wasm-git", "0.0.13"))

                // WASM specific dependencies if needed
            }
        }

        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test-wasm-js"))
            }
        }
    }
}

// Temporarily disabled: npm publish plugin doesn't support wasmJs targets
/*
npmPublish {
    organization.set("xiuper")

    packages {
        named("js") {
            packageJson {
                name = "@xiuper/mpp-core"
                version = project.version.toString()
                main = "xiuper-mpp-core.js"
                types = "xiuper-mpp-core.d.ts"
                description.set("AutoDev Xiuper Core - One Platform. All Phases. Every Device.")
                author {
                    name.set("Unit Mesh")
                    email.set("h@phodal.com")
                }
                license.set("MIT")
                private.set(false)
                repository {
                    type.set("git")
                    url.set("https://github.com/phodal/auto-dev.git")
                }
                keywords.set(listOf("ai4sdl", "ai", "llm", "agent"))
            }
        }
    }
}
*/

// Disable wasmJs browser tests due to webpack compatibility issues
// See: https://github.com/webpack/webpack/issues/XXX
// The wasmJs library will still be built, but browser tests are skipped
tasks.named("wasmJsBrowserTest") {
    enabled = false
}

// Configure JVM tests to run serially to avoid database conflicts
tasks.named<Test>("jvmTest") {
    maxParallelForks = 1
}

// Local scenario-based NanoDSL harness (uses active LLM config)
tasks.register<JavaExec>("runNanoDslScenarioHarness") {
    group = "application"
    description = "Generate scenarios -> NanoDSL -> validate -> save cases under docs/test-scripts/nanodsl-cases"
    mainClass.set("cc.unitmesh.devins.test.NanoDslScenarioHarness")
    dependsOn("jvmMainClasses")
    classpath = kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles +
        files(kotlin.jvm().compilations.getByName("main").output.classesDirs)
}

// Capture raw ACP protocol events to JSONL for analysis
tasks.register<JavaExec>("runAcpCapture") {
    group = "application"
    description = "Capture raw ACP protocol events to JSONL for analysis"
    mainClass.set("cc.unitmesh.agent.acp.AcpEventCapture")

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    if (project.hasProperty("acpPrompt")) {
        systemProperty("acpPrompt", project.property("acpPrompt") as String)
    }
    standardInput = System.`in`
}

// Task to generate a test .unit file
tasks.register<JavaExec>("generateTestUnit") {
    group = "verification"
    description = "Generate a test .unit file for verification"

    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmCompilation.output, configurations["jvmRuntimeClasspath"])
    mainClass.set("cc.unitmesh.agent.artifact.GenerateTestUnitKt")
}
