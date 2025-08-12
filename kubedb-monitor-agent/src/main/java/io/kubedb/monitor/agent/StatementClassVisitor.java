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
        
        // Add debug logging to verify method interception is working
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitLdcInsn("🔧 AGENT DEBUG: JDBC Method intercepted: " + className + "." + methodName);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        
        // Insert call to JDBCMethodInterceptor.executeStatement before the original method
        if ("execute".equals(methodName) || "executeQuery".equals(methodName) || "executeUpdate".equals(methodName)) {
            // Try to extract SQL from method parameters or statement object
            // For now, use more realistic placeholder values for PostgreSQL
            
            mv.visitVarInsn(Opcodes.ALOAD, 0); // 'this' (statement object)
            
            // Try to get SQL parameter if it exists (for execute(String sql) methods)
            if (methodDescriptor.contains("Ljava/lang/String;")) {
                mv.visitVarInsn(Opcodes.ALOAD, 1); // SQL parameter
            } else {
                mv.visitLdcInsn("INTERCEPTED_SQL_QUERY"); // Placeholder for prepared statements
            }
            
            // Use PostgreSQL connection details
            mv.visitLdcInsn("jdbc:postgresql://postgres-cluster-rw.postgres-system:5432/university");
            mv.visitLdcInsn("postgresql");
            
            // Call JDBCMethodInterceptor.executeStatement
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                "io/kubedb/monitor/agent/JDBCMethodInterceptor", 
                "executeStatement", 
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z", 
                false);
            
            // Pop the return value since we don't use it here
            mv.visitInsn(Opcodes.POP);
            
            // Additional debug log for TPS tracking
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("✅ AGENT DEBUG: JDBCMethodInterceptor.executeStatement called for TPS tracking");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        }
    }
}