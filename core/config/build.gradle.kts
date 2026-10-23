plugins {
    id("mvvm.android.library")
}

android { namespace = "com.will.mvvmbase.core.config" }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
