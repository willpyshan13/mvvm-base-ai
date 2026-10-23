plugins {
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

val checkModuleBoundaries = tasks.register("checkModuleBoundaries") {
    group = "verification"
    description = "Checks Gradle project dependency direction and cycles."
    doLast {
        val projectsByPath = rootProject.allprojects.associateBy { it.path }
        val edges = projectsByPath.values.associate { project ->
            project.path to project.configurations.flatMap { configuration ->
                configuration.dependencies.withType(
                    org.gradle.api.artifacts.ProjectDependency::class.java,
                ).map { it.path }
            }.filter { it != project.path }.toSet()
        }
        val violations = mutableListOf<String>()
        edges.forEach { (source, targets) ->
            targets.forEach { target ->
                val sourceParts = source.split(':').filter(String::isNotEmpty)
                val targetParts = target.split(':').filter(String::isNotEmpty)
                when {
                    sourceParts.firstOrNull() in setOf("core", "feature") && target == ":app" ->
                        violations += "${source} must not depend on app"
                    sourceParts.firstOrNull() == "core" && targetParts.firstOrNull() == "feature" ->
                        violations += "${source} must not depend on ${target}"
                    sourceParts.firstOrNull() == "feature" && targetParts.firstOrNull() == "feature" -> {
                        val sameFeature = sourceParts.getOrNull(1) == targetParts.getOrNull(1)
                        val targetLayer = targetParts.getOrNull(2)
                        if (!sameFeature && targetLayer !in setOf("api", "domain")) {
                            violations += "${source} may only depend on another feature's api/domain: ${target}"
                        }
                        if (sameFeature && targetLayer in setOf("data", "impl")) {
                            violations += "${source} has a reversed layer dependency on ${target}"
                        }
                    }
                }
            }
        }
        val visited = mutableSetOf<String>()
        val active = mutableSetOf<String>()
        fun visit(path: String, chain: List<String>) {
            if (path in active) {
                violations += "dependency cycle: ${(chain + path).joinToString(" -> ")}"
                return
            }
            if (!visited.add(path)) return
            active += path
            edges[path].orEmpty().forEach { visit(it, chain + path) }
            active -= path
        }
        edges.keys.forEach { visit(it, emptyList()) }
        if (violations.isNotEmpty()) throw GradleException(violations.distinct().joinToString("\n"))
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkModuleBoundaries) }
}

