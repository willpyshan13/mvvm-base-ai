plugins {
    id("mvvm.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.will.mvvmbase.feature.feed.data"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":feature:feed:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:config"))
    implementation(project(":core:apm"))
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.hilt.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
}
