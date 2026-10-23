plugins {
    id("mvvm.android.compose-library")
}

android {
    namespace = "com.will.mvvmbase.core.designsystem"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.okhttp)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
}
