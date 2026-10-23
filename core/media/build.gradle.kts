plugins {
    id("mvvm.android.compose-library")
}

android {
    namespace = "com.will.mvvmbase.core.media"
    ndkVersion = libs.versions.ndk.get()
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared" } }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.okhttp)
}
