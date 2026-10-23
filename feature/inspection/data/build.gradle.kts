plugins {
    id("mvvm.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android { namespace = "com.will.mvvmbase.feature.inspection.data" }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":feature:inspection:domain"))
    implementation(project(":core:apm"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.hilt.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
}
