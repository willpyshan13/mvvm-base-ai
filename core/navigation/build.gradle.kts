plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
