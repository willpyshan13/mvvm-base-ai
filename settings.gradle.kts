pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mvvm-base-ai"
include(
    ":feature:connectivity:api",
    ":feature:connectivity:impl",
    ":app",
    ":core:common",
    ":core:platform",
    ":core:apm",
    ":core:config",
    ":feature:feed:api",
    ":feature:feed:domain",
    ":feature:feed:data",
    ":feature:feed:impl",
    ":feature:inspection:api",
    ":feature:inspection:domain",
    ":feature:inspection:data",
    ":feature:inspection:impl",
    ":feature:toolbox:api",
    ":feature:toolbox:impl",
    ":core:network",
    ":core:designsystem",
    ":core:media",
    ":core:navigation",
    ":feature:home:domain",
    ":feature:home:data",
    ":feature:home:api",
    ":feature:home:impl",
    ":feature:settings:domain",
    ":feature:settings:data",
    ":feature:settings:api",
    ":feature:settings:impl",
    ":feature:status:api",
    ":feature:status:domain",
    ":feature:status:data",
    ":feature:status:impl",
    ":feature:media:api",
    ":feature:media:impl",
    ":feature:auth:domain",
    ":feature:auth:api",
    ":feature:auth:data",
    ":feature:auth:impl",
)


if (providers.gradleProperty("performanceTests").orNull == "true") include(":benchmark")
