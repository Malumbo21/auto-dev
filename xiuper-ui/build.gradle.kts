plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

group = "cc.unitmesh"
version = project.findProperty("mppVersion") as String? ?: "0.1.5"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    // JVM target
    jvm()

    // WasmJs target
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":mpp-core"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
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
                // JVM-specific logging
                implementation(libs.kotlin.logging.jvm)
                implementation(libs.logback)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }

        val wasmJsMain by getting {
            dependencies {
                // WasmJs-specific dependencies (if any)
            }
        }
    }
}

// Configure integration test source set for JVM only
val jvmIntegrationTest by kotlin.jvm().compilations.creating {
    defaultSourceSet {
        dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    associateWith(kotlin.jvm().compilations.getByName("main"))
    associateWith(kotlin.jvm().compilations.getByName("test"))
}

tasks.register<Test>("jvmIntegrationTest") {
    group = "verification"
    description = "Run NanoDSL integration tests that call LLM and verify DSL compilation"
    testClassesDirs = jvmIntegrationTest.output.classesDirs
    classpath = jvmIntegrationTest.runtimeDependencyFiles
    useJUnitPlatform()

    // Pass environment variables for LLM configuration
    environment("OPENAI_API_KEY", System.getenv("OPENAI_API_KEY") ?: "")
    environment("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY") ?: "")
    environment("DEEPSEEK_API_KEY", System.getenv("DEEPSEEK_API_KEY") ?: "")

    // Integration tests may take longer
    systemProperty("junit.jupiter.execution.timeout.default", "5m")

    // Show output for debugging
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

// Custom task to run DSL evaluation tests (JVM only)
tasks.register<JavaExec>("runDslEval") {
    group = "verification"
    description = "Run NanoDSL AI evaluation tests"
    mainClass.set("cc.unitmesh.xuiper.eval.DslEvalRunnerKt")
    classpath = kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles

    // Pass environment variables for LLM configuration
    environment("OPENAI_API_KEY", System.getenv("OPENAI_API_KEY") ?: "")
    environment("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY") ?: "")
    environment("DEEPSEEK_API_KEY", System.getenv("DEEPSEEK_API_KEY") ?: "")
}

// Task to validate DSL files (JVM only)
tasks.register<JavaExec>("validateDsl") {
    group = "verification"
    description = "Validate NanoDSL files in a directory"
    mainClass.set("cc.unitmesh.xuiper.eval.DslValidatorKt")
    classpath = kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles

    val dslDir = project.findProperty("dslDir") as? String ?: "testcases/actual/integration"
    val verbose = if (project.hasProperty("verbose")) "verbose" else ""
    args = listOf(dslDir, verbose)
}

// Task to render DSL files to HTML (JVM only)
tasks.register<JavaExec>("renderHtml") {
    group = "verification"
    description = "Render NanoDSL files to HTML"
    mainClass.set("cc.unitmesh.xuiper.eval.DslToHtmlRendererKt")
    classpath = kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles

    val inputDir = project.findProperty("dslDir") as? String ?: "testcases/actual/integration"
    val outputDir = project.findProperty("outputDir") as? String ?: "testcases/html/integration"
    args = listOf(inputDir, outputDir)
}

