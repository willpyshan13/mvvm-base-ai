plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(project(":core:navigation"))
    implementation(libs.kotlinx.serialization.core)
}
