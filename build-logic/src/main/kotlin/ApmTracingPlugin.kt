import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Plugin
import org.gradle.api.Project

class ApmTracingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withId("com.android.application") {
            project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java).onVariants { variant ->
                configureInstrumentation(variant)
            }
        }
        project.plugins.withId("com.android.library") {
            project.extensions.getByType(LibraryAndroidComponentsExtension::class.java).onVariants { variant ->
                configureInstrumentation(variant)
            }
        }
    }

    private fun configureInstrumentation(variant: Variant) {
        variant.instrumentation.transformClassesWith(
            ApmTraceClassVisitorFactory::class.java,
            InstrumentationScope.PROJECT,
        ) { }
        variant.instrumentation.setAsmFramesComputationMode(
            com.android.build.api.instrumentation.FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS,
        )
    }
}

abstract class ApmTraceClassVisitorFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun isInstrumentable(classData: ClassData): Boolean {
        val name = classData.className
        return !name.startsWith("com.will.mvvmbase.core.apm.") &&
            !name.contains(".BuildConfig") && !name.endsWith(".R") &&
            !name.contains("_Factory") && !name.contains("_MembersInjector") &&
            !name.contains("_HiltModules") && !name.contains("_GeneratedInjector")
    }

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: org.objectweb.asm.ClassVisitor) =
        ApmTraceClassVisitor(nextClassVisitor)
}
