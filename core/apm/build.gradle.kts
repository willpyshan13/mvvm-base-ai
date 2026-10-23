plugins {
    id("mvvm.android.library")
}

android {
    namespace = "com.will.mvvmbase.core.apm"
}

dependencies {
    implementation(platform(libs.opentelemetry.android.bom))
    implementation(libs.opentelemetry.android.agent)
    implementation(libs.okhttp)
    implementation(libs.errorprone.annotations)
    implementation(libs.kotlinx.coroutines.core)
    api(libs.androidx.room.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.opentelemetry.sdk.testing)
}
