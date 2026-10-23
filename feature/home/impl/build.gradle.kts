plugins {
    id("mvvm.android.compose-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.will.mvvmbase.feature.home"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":feature:home:api"))
    implementation(project(":feature:home:domain"))
    implementation(project(":feature:media:api"))
    implementation(project(":feature:settings:api"))
    implementation(project(":feature:status:api"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

