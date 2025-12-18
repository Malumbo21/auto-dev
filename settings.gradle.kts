rootProject.name = "Xiuper"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

//include("mpp-linter")
include("mpp-core")
include("mpp-ui")
include("mpp-codegraph")
include("mpp-server")
include("mpp-viewer")
include("mpp-viewer-web")
include("xiuper-ui")
include("xiuper-fs")

// IDEA plugin as composite build
includeBuild("mpp-idea")
