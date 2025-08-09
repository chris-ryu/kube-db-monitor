package io.kubedb.monitor.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM ClassVisitor for instrumenting JDBC Statement classes
 */
public class StatementClassVisitor extends ClassVisitor {
    
    private final String className;
    
    public StatementClassVisitor(ClassVisitor classVisitor, String className) {
        super(Opcodes.ASM9, classVisitor);
        this.className = className;
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        
        // Intercept execute methods
        if (isExecuteMethod(name, descriptor)) {
            return new StatementMethodVisitor(mv, className, name, descriptor);
        }
        
        return mv;
    }
    
    private boolean isExecuteMethod(String methodName, String descriptor) {
        // Target methods to intercept
        return ("execute".equals(methodName) || 
                "executeQuery".equals(methodName) || 
                "executeUpdate".equals(methodName)) &&
               descriptor != null;
    }
}

/**
 * ASM MethodVisitor for instrumenting JDBC Statement methods
 */
class StatementMethodVisitor extends MethodVisitor {
    
    private final String className;
    private final String methodName;
    private final String methodDescriptor;
    
    public StatementMethodVisitor(MethodVisitor methodVisitor, String className, String methodName, String methodDescriptor) {
        super(Opcodes.ASM9, methodVisitor);
        this.className = className;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
    }
    
    @Override
    public void visitCode() {
        super.visitCode();
        
        // Insert interception code at the beginning of the method
        // This is a simplified version - real implementation would be more complex
        
        // For now, just add a debug log
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("JDBC Method intercepted: " + className + "." + methodName);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    }
}