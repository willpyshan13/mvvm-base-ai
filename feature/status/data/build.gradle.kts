plugins {
    id("mvvm.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android { namespace = "com.will.mvvmbase.feature.status.data" }

dependencies {
    implementation(project(":feature:status:domain"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
