plugins { id("mvvm.android.library") }
android { namespace = "com.will.mvvmbase.core.platform"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}
dependencies {
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    implementation(libs.androidx.work)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
