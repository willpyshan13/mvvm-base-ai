plugins {
    id("mvvm.android.compose-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android { namespace = "com.will.mvvmbase.feature.connectivity" }

dependencies {
    implementation(project(":feature:connectivity:api"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
