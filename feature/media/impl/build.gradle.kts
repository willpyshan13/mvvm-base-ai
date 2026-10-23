plugins {
    id("mvvm.android.compose-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android { namespace = "com.will.mvvmbase.feature.media" }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:media"))
    implementation(project(":core:platform"))
    implementation(project(":core:config"))
    implementation(libs.androidx.work)
    implementation(libs.androidx.activity.compose)
    implementation(project(":core:navigation"))
    implementation(project(":feature:media:api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
