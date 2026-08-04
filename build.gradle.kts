// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    group = "com.github.hoanganhtuan95ptit.LayoutNode"
    version = "1.2.1.19"
}

tasks.register("publishLibrariesToMavenLocal") {
    group = "publishing"
    description = "Publishes node-engine and glide-loader release artifacts to Maven local."
    dependsOn(
        ":node-engine:publishReleasePublicationToMavenLocal",
        ":glide-loader:publishReleasePublicationToMavenLocal"
    )
}
