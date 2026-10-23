plugins {
    id("mvvm.android.compose-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android { namespace = "com.will.mvvmbase.feature.feed" }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:feed:domain"))
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(project(":core:navigation"))
    implementation(project(":feature:feed:api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
