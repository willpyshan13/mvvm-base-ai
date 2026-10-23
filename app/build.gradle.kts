val appEnvironment = providers.gradleProperty("appEnvironment").orElse("development").get()
require(appEnvironment in setOf("development", "staging", "production")) { "Unknown appEnvironment" }
val demoAuth = providers.gradleProperty("demoAuth").orElse("false").get().toBoolean()
require(!demoAuth || appEnvironment != "production") { "Demo authentication is forbidden in production" }
val apiBaseUrl = providers.gradleProperty("apiBaseUrl").orElse("").get().trim()
require(apiBaseUrl.isBlank() || apiBaseUrl.startsWith("https://")) { "API must use HTTPS" }
val escapedApiBaseUrl = apiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val apmEndpoint = providers.gradleProperty("apmEndpoint").orElse("").get().trim()
require(apmEndpoint.isBlank() || apmEndpoint.startsWith("https://")) { "APM endpoint must use HTTPS" }
val apmEnabled = providers.gradleProperty("apmEnabled").orElse(apmEndpoint.isNotBlank().toString()).get().toBooleanStrict()
require(!apmEnabled || apmEndpoint.isNotBlank()) { "Set -PapmEndpoint=https://... when APM is enabled" }
val apmGwpAsanMode = providers.gradleProperty("apmGwpAsanMode").orElse("default").get()
require(apmGwpAsanMode in setOf("default", "always", "never")) { "apmGwpAsanMode must be default, always, or never" }
val escapedApmEndpoint = apmEndpoint.replace("\\", "\\\\").replace("\"", "\\\"")
val featureFlagsUrl = providers.gradleProperty("featureFlagsUrl").orElse("").get().trim()
require(featureFlagsUrl.isBlank() || featureFlagsUrl.startsWith("https://")) { "Feature flag URL must use HTTPS" }
val escapedFeatureFlagsUrl = featureFlagsUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val appVersionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
val appVersionName = providers.gradleProperty("versionName").orElse("1.0.0").get()
require(appVersionCode > 0) { "versionCode must be a positive integer" }
require(appVersionName.isNotBlank()) { "versionName must not be blank" }
val releaseSigningEnvironmentNames = listOf(
    "ANDROID_KEYSTORE_PATH",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningEnvironmentNames.associateWith { name ->
    providers.environmentVariable(name).orNull?.takeIf(String::isNotBlank)
}
val configuredReleaseSigningValues = releaseSigningValues.values.count { it != null }
if (configuredReleaseSigningValues != 0 && configuredReleaseSigningValues != releaseSigningEnvironmentNames.size) {
    throw GradleException(
        "Release signing requires all of: ${releaseSigningEnvironmentNames.joinToString()}",
    )
}
val releaseSigningEnabled = configuredReleaseSigningValues == releaseSigningEnvironmentNames.size

plugins {
    id("mvvm.android.application")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

dependencyLocking {
    lockAllConfigurations()
}

android {
    compileOptions { isCoreLibraryDesugaringEnabled = true }
    namespace = "com.will.mvvmbase"

    defaultConfig {
        applicationId = "com.will.mvvmbase"
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") }
        versionCode = appVersionCode
        versionName = appVersionName
        if (appEnvironment != "production") applicationIdSuffix = ".${appEnvironment}${if (demoAuth) ".demo" else ""}"
        buildConfigField("String", "APP_ENVIRONMENT", "\"$appEnvironment\"")
        buildConfigField("boolean", "DEMO_AUTH", demoAuth.toString())
        buildConfigField("String", "API_BASE_URL", "\"$escapedApiBaseUrl\"")
        buildConfigField("String", "APM_ENDPOINT", "\"$escapedApmEndpoint\"")
        buildConfigField("boolean", "APM_ENABLED", apmEnabled.toString())
        buildConfigField("String", "FEATURE_FLAGS_URL", "\"$escapedFeatureFlagsUrl\"")
        manifestPlaceholders["apmGwpAsanMode"] = apmGwpAsanMode
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseSigningEnabled) {
        signingConfigs {
            create("release") {
                val keystore = file(requireNotNull(releaseSigningValues["ANDROID_KEYSTORE_PATH"]))
                if (!keystore.isFile) {
                    throw GradleException("ANDROID_KEYSTORE_PATH does not point to a file: $keystore")
                }
                storeFile = keystore
                storePassword = requireNotNull(releaseSigningValues["ANDROID_KEYSTORE_PASSWORD"])
                keyAlias = requireNotNull(releaseSigningValues["ANDROID_KEY_ALIAS"])
                keyPassword = requireNotNull(releaseSigningValues["ANDROID_KEY_PASSWORD"])
            }
        }
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

}

dependencies {
    implementation(project(":feature:connectivity:impl"))
    debugImplementation(libs.leakcanary.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":feature:home:data"))
    implementation(project(":feature:settings:data"))
    implementation(project(":feature:settings:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:platform"))
    implementation(project(":core:apm"))
    implementation(project(":core:config"))
    implementation(project(":feature:toolbox:api"))
    implementation(project(":feature:toolbox:impl"))
    implementation(project(":feature:feed:data"))
    implementation(project(":feature:feed:impl"))
    implementation(project(":feature:feed:api"))
    implementation(project(":feature:inspection:api"))
    implementation(project(":feature:inspection:data"))
    implementation(project(":feature:inspection:impl"))
    implementation(project(":feature:media:api"))
    implementation(project(":feature:settings:api"))
    implementation(project(":core:media"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:home:api"))
    implementation(project(":feature:home:impl"))
    implementation(project(":feature:media:impl"))
    implementation(project(":feature:settings:impl"))
    implementation(project(":feature:status:data"))
    implementation(project(":feature:status:api"))
    implementation(project(":feature:status:impl"))
    implementation(project(":feature:auth:api"))
    implementation(project(":feature:auth:domain"))
    implementation(project(":feature:auth:data"))
    implementation(project(":feature:auth:impl"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.androidx.work)
    ksp(libs.hilt.compiler)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
