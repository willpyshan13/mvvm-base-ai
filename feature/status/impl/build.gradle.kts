plugins {
    id("mvvm.android.compose-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android { namespace = "com.will.mvvmbase.feature.status" }

dependencies {
    implementation(project(":feature:status:api"))
    implementation(project(":feature:status:domain"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
