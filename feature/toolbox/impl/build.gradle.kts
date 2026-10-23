plugins {
    id("mvvm.android.compose-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android { namespace = "com.will.mvvmbase.feature.toolbox" }

dependencies {
    implementation(project(":feature:connectivity:api"))
    implementation(project(":core:common"))
    implementation(project(":core:platform"))
    implementation(project(":core:media"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:media:api"))
    implementation(project(":feature:status:api"))
    implementation(project(":feature:feed:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work)
    implementation(project(":core:navigation"))
    implementation(project(":feature:toolbox:api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
}
