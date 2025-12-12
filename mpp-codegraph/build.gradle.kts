@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
    // Note: npm publish plugin disabled temporarily due to wasmJs incompatibility
    // id("dev.petuska.npm.publish") version "3.5.3"
}

repositories {
    google()
    mavenCentral()
}

version = "0.1.0"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    js(IR) {
        useCommonJs()
        browser()
        nodejs()
        binaries.library()
        generateTypeScriptDefinitions()

        compilerOptions {
            moduleKind.set(org.jetbrains.kotlin.gradle.dsl.JsModuleKind.MODULE_ES)
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

    // iOS targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "CodeGraph"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
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
                // TreeSitter JVM bindings - matching SASK versions
                // see in https://github.com/bonede/tree-sitter-ng
                implementation(libs.treesitter)
                implementation(libs.treesitter.java)
                implementation(libs.treesitter.kotlin)
                implementation(libs.treesitter.csharp)
                implementation(libs.treesitter.javascript)
                implementation(libs.treesitter.python)
                implementation(libs.treesitter.rust)
                implementation(libs.treesitter.go)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        jsMain {
            dependencies {
                // web-tree-sitter for JS platform
                implementation(npm("web-tree-sitter", "0.22.2"))
                // TreeSitter WASM artifacts - matching autodev-workbench versions
                implementation(npm("@unit-mesh/treesitter-artifacts", "1.7.7"))
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        wasmJsMain {
            dependencies {
                // web-tree-sitter for WASM-JS platform (uses same npm packages as JS)
                implementation(npm("web-tree-sitter", "0.22.2"))
                // TreeSitter WASM artifacts
                implementation(npm("@unit-mesh/treesitter-artifacts", "1.7.7"))

                implementation(devNpm("copy-webpack-plugin", "12.0.2"))
            }
        }

        wasmJsTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        iosMain {
            dependencies {
                // iOS uses a simplified implementation without TreeSitter
                // TreeSitter native bindings are not available for iOS
            }
        }

        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        iosTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// npmPublish configuration disabled temporarily due to wasmJs incompatibility
// To publish JS package, manually configure npm package.json and use npm publish
//
// npmPublish {
//     organization.set("autodev")
//     packages {
//         named("js") {
//             packageJson {
//                 name = "@autodev/codegraph"
//                 version = project.version.toString()
//                 main = "autodev-codegraph.js"
//                 types = "autodev-codegraph.d.ts"
//                 description.set("AutoDev Code Graph - TreeSitter-based code analysis for Kotlin Multiplatform")
//                 author {
//                     name.set("Unit Mesh")
//                     email.set("h@phodal.com")
//                 }
//                 license.set("MIT")
//                 private.set(false)
//                 repository {
//                     type.set("git")
//                     url.set("https://github.com/unit-mesh/auto-dev.git")
//                 }
//                 keywords.set(listOf("kotlin", "multiplatform", "treesitter", "code-analysis", "ast"))
//             }
//         }
//     }
// }

