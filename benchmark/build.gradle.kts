plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.will.mvvmbase.benchmark"
    compileSdk = 36
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val environment = providers.gradleProperty("appEnvironment").orElse("development").get()
        buildConfigField("String", "TARGET_PACKAGE", "\"com.will.mvvmbase${if (environment == "production") "" else ".$environment"}\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        create("benchmark") { isDebuggable = true; matchingFallbacks += "release" }
    }
    experimentalProperties["android.experimental.self-instrumenting"] = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
}
