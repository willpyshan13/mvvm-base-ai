plugins {
    id("mvvm.android.library")
}

android {
    namespace = "com.will.mvvmbase.core.common"
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
