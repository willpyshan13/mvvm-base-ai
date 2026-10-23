plugins {
    id("mvvm.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.will.mvvmbase.feature.auth.data"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    implementation(project(":core:network"))
    implementation(project(":core:apm"))
    implementation(project(":feature:auth:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
