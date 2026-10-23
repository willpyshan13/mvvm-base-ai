import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

private const val TRACE_ANNOTATION = "Lcom/will/mvvmbase/core/apm/ApmMonitoredMethod;"
private const val BRIDGE_OWNER = "com/will/mvvmbase/core/apm/ApmMethodTraceBridge"

class ApmTraceClassVisitor(next: ClassVisitor) : ClassVisitor(Opcodes.ASM9, next) {
    private var className: String = ""

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        className = name.replace('/', '.')
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
        if (delegate == null || access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_NATIVE) != 0 ||
            name == "<init>" || name == "<clinit>" ||
            descriptor.contains("Lkotlin/coroutines/Continuation;")
        ) return delegate

        val methodName = "$className#$name"
        return object : MethodVisitor(Opcodes.ASM9, delegate) {
            private var isMonitored = false
            private var thresholdMillis = 16L

            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                val annotation = super.visitAnnotation(descriptor, visible)
                if (descriptor != TRACE_ANNOTATION) return annotation
                isMonitored = true
                return object : AnnotationVisitor(Opcodes.ASM9, annotation) {
                    override fun visit(name: String?, value: Any?) {
                        if (name == "slowThresholdMillis" && value is Long) thresholdMillis = value
                        super.visit(name, value)
                    }
                }
            }

            override fun visitCode() {
                super.visitCode()
                if (isMonitored) {
                    visitLdcInsn(methodName)
                    visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE_OWNER, "begin", "(Ljava/lang/String;)V", false)
                }
            }

            override fun visitInsn(opcode: Int) {
                if (isMonitored && opcode in Opcodes.IRETURN..Opcodes.RETURN) {
                    visitLdcInsn(methodName)
                    visitLdcInsn(thresholdMillis)
                    visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        BRIDGE_OWNER,
                        "end",
                        "(Ljava/lang/String;J)V",
                        false,
                    )
                } else if (isMonitored && opcode == Opcodes.ATHROW) {
                    visitInsn(Opcodes.DUP)
                    visitLdcInsn(methodName)
                    visitLdcInsn(thresholdMillis)
                    visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        BRIDGE_OWNER,
                        "fail",
                        "(Ljava/lang/Throwable;Ljava/lang/String;J)V",
                        false,
                    )
                }
                super.visitInsn(opcode)
            }

            override fun visitMaxs(maxStack: Int, maxLocals: Int) {
                super.visitMaxs(if (isMonitored) maxStack + 5 else maxStack, maxLocals)
            }
        }
    }
}
