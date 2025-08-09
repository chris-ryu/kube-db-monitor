package io.kubedb.monitor.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * JDBC ClassFileTransformer that intercepts JDBC calls for monitoring
 */
public class JDBCInterceptor implements ClassFileTransformer {
    private static final Logger logger = LoggerFactory.getLogger(JDBCInterceptor.class);
    
    private final AgentConfig config;
    
    // Target classes to instrument
    private static final String[] TARGET_CLASSES = {
        "java/sql/Connection",
        "java/sql/Statement", 
        "java/sql/PreparedStatement",
        "java/sql/CallableStatement"
    };
    
    public JDBCInterceptor(AgentConfig config) {
        this.config = config;
        logger.info("JDBCInterceptor initialized with config: {}", config);
    }
    
    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) throws IllegalClassFormatException {
        
        // Skip if monitoring is disabled
        if (!config.isEnabled()) {
            return null;
        }
        
        // Check if this is a target class
        if (!isTargetClass(className)) {
            return null;
        }
        
        try {
            logger.debug("Instrumenting class: {}", className);
            
            // TODO: Implement ASM bytecode transformation
            // For now, return null (no transformation)
            return transformClass(className, classfileBuffer);
            
        } catch (Exception e) {
            logger.error("Failed to transform class: {}", className, e);
            // Return null to avoid breaking the application
            return null;
        }
    }
    
    private boolean isTargetClass(String className) {
        if (className == null) {
            return false;
        }
        
        for (String targetClass : TARGET_CLASSES) {
            if (className.equals(targetClass)) {
                return true;
            }
        }
        
        // Also check for database driver classes
        return isDriverClass(className);
    }
    
    private boolean isDriverClass(String className) {
        // Check for common JDBC driver classes
        return className.contains("mysql") ||
               className.contains("postgresql") ||
               className.contains("oracle") ||
               className.contains("h2") ||
               className.contains("jdbc");
    }
    
    private byte[] transformClass(String className, byte[] classfileBuffer) {
        try {
            // Use ASM to transform the class
            org.objectweb.asm.ClassReader classReader = new org.objectweb.asm.ClassReader(classfileBuffer);
            org.objectweb.asm.ClassWriter classWriter = new org.objectweb.asm.ClassWriter(
                    classReader, 
                    org.objectweb.asm.ClassWriter.COMPUTE_MAXS | org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
            );
            
            // Apply our transformation visitor
            StatementClassVisitor visitor = new StatementClassVisitor(classWriter, className);
            classReader.accept(visitor, org.objectweb.asm.ClassReader.EXPAND_FRAMES);
            
            byte[] transformedClass = classWriter.toByteArray();
            
            logger.info("Successfully transformed class: {}", className);
            return transformedClass;
            
        } catch (Exception e) {
            logger.error("Failed to transform class: {}", className, e);
            // Return null to indicate no transformation should be applied
            return null;
        }
    }
}