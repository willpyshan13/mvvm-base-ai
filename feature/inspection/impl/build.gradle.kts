plugins {
    id("mvvm.android.compose-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android { namespace = "com.will.mvvmbase.feature.inspection" }

dependencies {
    implementation(project(":feature:inspection:api"))
    implementation(project(":feature:inspection:domain"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
