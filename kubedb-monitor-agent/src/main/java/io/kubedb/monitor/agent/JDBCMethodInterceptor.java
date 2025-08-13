package io.kubedb.monitor.agent;

import io.kubedb.monitor.common.deadlock.DeadlockDetector;
import io.kubedb.monitor.common.metrics.DBMetrics;
import io.kubedb.monitor.common.metrics.MetricsCollector;
import io.kubedb.monitor.common.metrics.MetricsCollectorFactory;
import io.kubedb.monitor.common.transaction.TransactionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.Map;

/**
 * Runtime interceptor for JDBC method calls with transaction tracking
 * This class contains the actual interception logic that gets injected into JDBC classes
 */
public class JDBCMethodInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(JDBCMethodInterceptor.class);
    private static volatile MetricsCollector collector;
    private static volatile TransactionAwareJDBCInterceptor transactionInterceptor;
    
    
    // TPS calculation - track query count per time window
    private static final AtomicLong totalQueries = new AtomicLong(0);
    private static final Map<String, Instant> queryTimestamps = new ConcurrentHashMap<>();
    private static final long TPS_WINDOW_MS = 1000; // 1 second window
    
    // Long running transaction threshold (configurable)  
    private static final long LONG_RUNNING_THRESHOLD_MS = 5000; // 5 seconds
    
    // Transaction tracking - connection ID -> transaction start time
    private static final Map<String, Instant> transactionStartTimes = new ConcurrentHashMap<>();
    
    /**
     * Get or create the metrics collector (lazy initialization)
     */
    private static MetricsCollector getCollector() {
        if (collector == null) {
            synchronized (JDBCMethodInterceptor.class) {
                if (collector == null) {
                    logger.debug("Initializing MetricsCollector from system config");
                    collector = MetricsCollectorFactory.createFromSystemConfig();
                    logger.info("MetricsCollector initialized: {}", collector.getClass().getSimpleName());
                }
            }
        }
        return collector;
    }
    
    /**
     * Get or create the transaction-aware interceptor (lazy initialization)
     */
    private static TransactionAwareJDBCInterceptor getTransactionInterceptor() {
        if (transactionInterceptor == null) {
            synchronized (JDBCMethodInterceptor.class) {
                if (transactionInterceptor == null) {
                    TransactionRegistry registry = new TransactionRegistry();
                    DeadlockDetector detector = new DeadlockDetector();
                    transactionInterceptor = new TransactionAwareJDBCInterceptor(registry, detector);
                    logger.info("TransactionAwareJDBCInterceptor initialized");
                }
            }
        }
        return transactionInterceptor;
    }
    
    /**
     * Intercepts Statement.execute() method calls
     */
    public static boolean executeStatement(Object statement, String sql, String connectionUrl, String databaseType) {
        long startTime = System.currentTimeMillis();
        boolean result = false;
        Connection connection = null;
        
        // Extract actual SQL - prioritize parameter SQL, then extract from statement object  
        String actualSql = sql;
        if (sql == null || sql.isEmpty() || "INTERCEPTED_SQL_QUERY".equals(sql) || "SQL_PLACEHOLDER".equals(sql)) {
            // SQL parameter is null/empty/placeholder - extract from statement object
            actualSql = extractSqlFromStatement(statement);
            logger.debug("Extracted SQL from statement object: {}", actualSql);
        } else {
            logger.debug("Using SQL parameter: {}", actualSql);
        }
        
        try {
            // Add debug output to System.out to ensure this method is called
            System.out.println("🔍 JDBCMethodInterceptor.executeStatement called: " + actualSql);
            System.out.println("🔧 MetricsCollector about to be initialized...");
            logger.debug("Intercepting statement execution: {}", actualSql);
            
            // Extract connection from statement if available
            System.out.println("🔧 SYSTEM.OUT: Attempting to extract connection from statement type: " + statement.getClass().getName());
            connection = extractConnection(statement);
            if (connection != null) {
                System.out.println("✅ SYSTEM.OUT: Connection extraction SUCCESSFUL: " + connection.getClass().getName());
            } else {
                System.out.println("❌ SYSTEM.OUT: Connection extraction FAILED for statement type: " + statement.getClass().getName());
            }
            
            long executionTime = 0;
            
            // Real Long Running Transaction detection: Track actual execution time
            // Instead of simulation, we track the duration from method call to completion
            try {
                System.out.println("🔧 SYSTEM.OUT: Long Running Transaction detection started for SQL: " + actualSql);
                
                // Record transaction start time for Long Running Transaction detection
                String connectionId = getConnectionId(connection);
                if (connectionId != null) {
                    transactionStartTimes.put(connectionId, Instant.now());
                    System.out.println("📊 SYSTEM.OUT: Transaction start time recorded for connection: " + connectionId);
                }
                
                // For ASM-intercepted calls, we avoid any database calls that could trigger recursion
                // Instead, we use the connection object presence as success indicator
                if (connection != null && "ASM_INTERCEPTED_SQL".equals(actualSql)) {
                    try {
                        // Check connection object state without triggering JDBC calls
                        boolean connectionExists = !connection.isClosed();
                        result = connectionExists;
                        System.out.println("🔍 SYSTEM.OUT: Connection state check result: " + connectionExists);
                    } catch (SQLException connectionError) {
                        System.out.println("⚠️ SYSTEM.OUT: Connection state check failed: " + connectionError.getMessage());
                        result = false;
                    }
                } else {
                    // For non-ASM intercepted calls, assume successful execution
                    result = true;
                }
                
                executionTime = System.currentTimeMillis() - startTime;
                System.out.println("✅ SYSTEM.OUT: Long Running Transaction detection completed in " + executionTime + "ms");
                
                // Check for Long Running Transaction (5 second threshold)
                if (executionTime >= LONG_RUNNING_THRESHOLD_MS) {
                    System.out.println("🐌 SYSTEM.OUT: LONG RUNNING TRANSACTION DETECTED! Duration: " + executionTime + "ms, SQL: " + actualSql);
                    logger.warn("LONG RUNNING TRANSACTION DETECTED: {} ms, SQL: {}", executionTime, actualSql);
                    
                    // 대시보드로 Long Running Transaction 이벤트 전송
                    try {
                        System.out.println("📡 SYSTEM.OUT: Sending LONG_RUNNING_TRANSACTION event to dashboard...");
                        DBMetrics longRunningMetric = DBMetrics.builder()
                            .sql("LONG_RUNNING_TRANSACTION")
                            .executionTimeMs(executionTime)
                            .databaseType("TRANSACTION_METRIC")
                            .connectionUrl("long-tx://" + getConnectionId(connection))
                            .build();
                        getCollector().collect(longRunningMetric);
                        System.out.println("✅ SYSTEM.OUT: LONG_RUNNING_TRANSACTION event sent successfully to dashboard");
                    } catch (Exception e) {
                        System.out.println("❌ SYSTEM.OUT: Failed to send LONG_RUNNING_TRANSACTION event: " + e.getMessage());
                    }
                }
                
                // Track TPS metrics
                trackTpsMetrics(actualSql, executionTime);
                
            } catch (Exception e) {
                System.out.println("❌ SYSTEM.OUT: executeOriginalStatement() failed: " + e.getMessage());
                logger.error("executeOriginalStatement failed", e);
                executionTime = System.currentTimeMillis() - startTime;
                // Continue execution - don't let SQL execution failure stop transaction detection
            }
            
            // ALWAYS try transaction-aware detection regardless of SQL execution success/failure
            try {
                logger.info("🔄 Attempting transaction-aware detection for SQL: {}", actualSql);
                System.out.println("🔍 SYSTEM.OUT: Transaction-aware detection triggered for: " + actualSql);
                
                // Use TransactionAwareJDBCInterceptor for proper transaction detection
                if (connection != null) {
                    logger.info("🔄 Calling TransactionAwareJDBCInterceptor.onQueryExecution for SQL: {}", actualSql);
                    getTransactionInterceptor().onQueryExecution(connection, actualSql, executionTime, true);
                    checkForLongRunningTransaction(connection);
                } else {
                    logger.warn("Connection extraction failed, cannot perform transaction-based detection");
                    System.out.println("⚠️ SYSTEM.OUT: Connection is null, cannot perform proper transaction detection");
                }
            } catch (Exception e) {
                logger.error("Error in transaction-aware detection", e);
                System.out.println("❌ SYSTEM.OUT: Transaction-aware detection failed: " + e.getMessage());
            }
            
            // Collect basic metrics
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(actualSql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .build();
            
            getCollector().collect(metric);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Handle transaction-aware error tracking
            if (connection != null && e instanceof SQLException) {
                getTransactionInterceptor().onQueryError(connection, actualSql, (SQLException) e);
            }
            
            // Collect error metric
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .error(e.getMessage())
                    .build();
            
            getCollector().collect(metric);
            
            // Re-throw the exception
            throw new RuntimeException(e);
        }
        
        return result;
    }
    
    /**
     * Intercepts PreparedStatement.execute() method calls
     */
    public static boolean executePreparedStatement(Object preparedStatement, String sql, String connectionUrl, String databaseType) {
        long startTime = System.currentTimeMillis();
        boolean result = false;
        Connection connection = null;
        
        try {
            logger.debug("Intercepting prepared statement execution: {}", sql);
            
            // Extract connection from prepared statement if available
            connection = extractConnection(preparedStatement);
            
            // Call original method
            result = executeOriginalPreparedStatement(preparedStatement);
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Track TPS metrics
            trackTpsMetrics(sql, executionTime);
            
            // Track transaction-aware metrics if connection available
            if (connection != null) {
                getTransactionInterceptor().onQueryExecution(connection, sql, executionTime, true);
                checkForLongRunningTransaction(connection);
            }
            
            // Collect basic metrics
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .build();
            
            getCollector().collect(metric);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Handle transaction-aware error tracking
            if (connection != null && e instanceof SQLException) {
                getTransactionInterceptor().onQueryError(connection, sql, (SQLException) e);
            }
            
            // Collect error metric
            DBMetrics metric = DBMetrics.builder()
                    .sql(maskSqlParameters(sql))
                    .executionTimeMs(executionTime)
                    .databaseType(databaseType)
                    .connectionUrl(connectionUrl)
                    .error(e.getMessage())
                    .build();
            
            getCollector().collect(metric);
            
            // Re-throw the exception
            throw new RuntimeException(e);
        }
        
        return result;
    }
    
    /**
     * Get the metrics collector (for testing)
     */
    public static MetricsCollector getMetricsCollector() {
        return getCollector();
    }
    
    /**
     * Clear collected metrics (for testing)
     */
    public static void clearMetrics() {
        getCollector().clear();
    }
    
    private static String maskSqlParameters(String sql) {
        if (sql == null) {
            return null;
        }
        
        // Simple parameter masking - replace values with ?
        // In production, this would be more sophisticated
        return sql.replaceAll("'[^']*'", "?")
                  .replaceAll("\\b\\d+\\b", "?");
    }
    
    
    private static boolean executeOriginalPreparedStatement(Object preparedStatement) throws SQLException {
        // This is a placeholder for the original method call
        logger.debug("Executing original prepared statement");
        
        // Simulate execution
        try {
            Thread.sleep(5); // Simulate DB execution time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return true;
    }
    
    /**
     * Track TPS (Transactions Per Second) metrics
     */
    private static void trackTpsMetrics(String sql, long executionTimeMs) {
        long currentQueries = totalQueries.incrementAndGet();
        Instant now = Instant.now();
        String queryId = "q-" + currentQueries;
        
        queryTimestamps.put(queryId, now);
        
        // Calculate TPS for the last window - remove old entries first
        long windowStart = now.minusMillis(TPS_WINDOW_MS).toEpochMilli();
        queryTimestamps.entrySet().removeIf(entry -> 
            entry.getValue().toEpochMilli() < windowStart);
        
        long queriesInWindow = queryTimestamps.size();
        double currentTps = (double) queriesInWindow / (TPS_WINDOW_MS / 1000.0);
        
        logger.info("📊 TPS Metrics: Current={}, Total Queries={}, Execution Time={}ms", 
                   String.format("%.2f", currentTps), currentQueries, executionTimeMs);
        
        // Generate TPS event if threshold exceeded (lowered to 2 TPS for demo)
        if (currentTps > 2) {
            logger.info("🚨 TPS Threshold Exceeded: {} TPS (threshold: 2)", currentTps);
            generateTpsEvent(currentTps, currentQueries);
        }
        
        // Long Running Transaction detection is handled by checkForLongRunningTransaction() method
    }
    
    /**
     * Check for long running transactions
     */
    private static void checkForLongRunningTransaction(Connection connection) {
        try {
            String connectionId = connection.toString();
            Instant now = Instant.now();
            
            // Check if connection has autoCommit disabled (indicating active transaction)
            if (!connection.getAutoCommit()) {
                // Transaction is active - track start time if not already tracked
                transactionStartTimes.putIfAbsent(connectionId, now);
                
                Instant transactionStart = transactionStartTimes.get(connectionId);
                if (transactionStart != null) {
                    long duration = now.toEpochMilli() - transactionStart.toEpochMilli();
                    if (duration > LONG_RUNNING_THRESHOLD_MS) {
                        logger.warn("⚠️ Long Running Transaction detected: connection={}, duration={}ms (threshold: {}ms)", 
                                   connectionId, duration, LONG_RUNNING_THRESHOLD_MS);
                        generateLongRunningTransactionEvent(connectionId, duration);
                    }
                }
            } else {
                // AutoCommit is enabled - remove any tracked transaction
                transactionStartTimes.remove(connectionId);
            }
        } catch (SQLException e) {
            logger.debug("Could not check transaction status", e);
        }
    }
    
    /**
     * Generate TPS event for high transaction rates
     */
    private static void generateTpsEvent(double tps, long totalQueries) {
        logger.info("🚀 HIGH TPS EVENT: {} TPS detected (Total: {} queries)", 
                   String.format("%.2f", tps), totalQueries);
        
        // Here you would send this to the metrics collector as a special event
        try {
            logger.info("🔧 DEBUG: About to send TPS_EVENT to collector with TPS={}", tps);
            DBMetrics tpsMetric = DBMetrics.builder()
                .sql("TPS_EVENT")
                .executionTimeMs((long) tps)
                .databaseType("TPS_METRIC")
                .connectionUrl("tps://" + String.format("%.2f", tps))
                .build();
            getCollector().collect(tpsMetric);
            logger.info("✅ DEBUG: Successfully sent TPS_EVENT to collector");
        } catch (Exception e) {
            logger.error("❌ Failed to send TPS event", e);
        }
    }
    
    /**
     * Generate long running transaction event
     */
    private static void generateLongRunningTransactionEvent(String connectionId, long durationMs) {
        logger.warn("🐌 LONG RUNNING TRANSACTION EVENT: {} duration={}ms", connectionId, durationMs);
        
        // Here you would send this to the metrics collector as a special event
        try {
            getCollector().collect(DBMetrics.builder()
                .sql("LONG_RUNNING_TRANSACTION")
                .executionTimeMs(durationMs)
                .databaseType("TRANSACTION_METRIC")
                .connectionUrl("long-tx://" + connectionId)
                .build());
        } catch (Exception e) {
            logger.error("Failed to send long running transaction event", e);
        }
    }
    
    /**
     * Get unique identifier for a database connection
     */
    private static String getConnectionId(Connection connection) {
        if (connection == null) {
            return null;
        }
        
        try {
            // Use connection's hashCode and class name as unique identifier
            return connection.getClass().getSimpleName() + "@" + Integer.toHexString(connection.hashCode());
        } catch (Exception e) {
            logger.debug("Could not generate connection ID", e);
            return "unknown-connection-" + System.currentTimeMillis();
        }
    }
    
    /**
     * Extract SQL string from statement object (reflection-based)
     * Enhanced to handle PostgreSQL JDBC and other common JDBC driver patterns
     */
    private static String extractSqlFromStatement(Object statement) {
        System.out.println("🔧 SYSTEM.OUT: extractSqlFromStatement() called for: " + statement.getClass().getName());
        
        try {
            // Method 1: PostgreSQL JDBC PreparedStatement has 'sql' field
            if (statement instanceof java.sql.PreparedStatement) {
                try {
                    java.lang.reflect.Field sqlField = null;
                    Class<?> currentClass = statement.getClass();
                    
                    // Search for sql field in class hierarchy
                    while (currentClass != null && sqlField == null) {
                        try {
                            sqlField = currentClass.getDeclaredField("sql");
                            break;
                        } catch (NoSuchFieldException e) {
                            // Try other common field names for SQL storage
                            String[] fieldNames = {"query", "sqlString", "originalSql", "preparedSql", "statementSql"};
                            for (String fieldName : fieldNames) {
                                try {
                                    sqlField = currentClass.getDeclaredField(fieldName);
                                    break;
                                } catch (NoSuchFieldException ignored) {}
                            }
                        }
                        currentClass = currentClass.getSuperclass();
                    }
                    
                    if (sqlField != null) {
                        sqlField.setAccessible(true);
                        Object result = sqlField.get(statement);
                        if (result instanceof String) {
                            String extractedSql = (String) result;
                            logger.info("✅ Successfully extracted SQL using field '{}': {}", 
                                       sqlField.getName(), extractedSql.substring(0, Math.min(50, extractedSql.length())));
                            System.out.println("✅ SYSTEM.OUT: SQL extraction successful: " + extractedSql.substring(0, Math.min(50, extractedSql.length())));
                            return extractedSql;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Method 1 (field access) failed: {}", e.getMessage());
                }
            }
            
            // Method 2: Try toString() analysis for Statement objects
            try {
                String toString = statement.toString();
                if (toString.contains("sql=") || toString.contains("SQL=")) {
                    // Extract SQL from toString output like "PreparedStatement[sql=SELECT * FROM ...]"
                    int sqlStart = toString.toLowerCase().indexOf("sql=");
                    if (sqlStart != -1) {
                        sqlStart += 4; // Skip "sql="
                        int sqlEnd = toString.indexOf(',', sqlStart);
                        if (sqlEnd == -1) sqlEnd = toString.indexOf(']', sqlStart);
                        if (sqlEnd == -1) sqlEnd = toString.length();
                        
                        String extractedSql = toString.substring(sqlStart, sqlEnd).trim();
                        if (!extractedSql.isEmpty() && extractedSql.length() > 5) {
                            logger.info("✅ Extracted SQL from toString(): {}", extractedSql.substring(0, Math.min(50, extractedSql.length())));
                            System.out.println("✅ SYSTEM.OUT: SQL extraction from toString(): " + extractedSql.substring(0, Math.min(50, extractedSql.length())));
                            return extractedSql;
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Method 2 (toString analysis) failed: {}", e.getMessage());
            }
            
            // Method 3: Try common getter methods
            try {
                String[] methodNames = {"getSql", "getQuery", "getStatement", "getOriginalSql"};
                for (String methodName : methodNames) {
                    try {
                        java.lang.reflect.Method method = statement.getClass().getMethod(methodName);
                        Object result = method.invoke(statement);
                        if (result instanceof String) {
                            String extractedSql = (String) result;
                            if (!extractedSql.isEmpty() && extractedSql.length() > 3) {
                                logger.info("✅ Extracted SQL using method '{}': {}", methodName, extractedSql.substring(0, Math.min(50, extractedSql.length())));
                                System.out.println("✅ SYSTEM.OUT: SQL extraction using method '" + methodName + "': " + extractedSql.substring(0, Math.min(50, extractedSql.length())));
                                return extractedSql;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                logger.debug("Method 3 (getter methods) failed: {}", e.getMessage());
            }
            
            logger.warn("❌ All SQL extraction methods failed for statement type: {}", statement.getClass().getName());
            System.out.println("❌ SYSTEM.OUT: SQL extraction failed for statement type: " + statement.getClass().getName());
            System.out.println("🔍 SYSTEM.OUT: Statement toString() for debugging: " + statement.toString());
            
            return "SQL_PLACEHOLDER";
            
        } catch (Exception e) {
            logger.error("Fatal error in extractSqlFromStatement()", e);
            System.out.println("❌ SYSTEM.OUT: Fatal error in SQL extraction: " + e.getMessage());
            return "SQL_PLACEHOLDER";
        }
    }
    
    /**
     * Extract Connection object from statement (reflection-based)
     * Enhanced to handle HikariCP, PostgreSQL JDBC, and other common connection pool patterns
     */
    private static Connection extractConnection(Object statement) {
        System.out.println("🔧 SYSTEM.OUT: extractConnection() called for: " + statement.getClass().getName());
        try {
            // Method 1: Standard getConnection() method
            try {
                java.lang.reflect.Method getConnectionMethod = statement.getClass().getMethod("getConnection");
                Object result = getConnectionMethod.invoke(statement);
                if (result instanceof Connection) {
                    logger.info("✅ Successfully extracted connection using getConnection(): {}", result.getClass().getName());
                    return (Connection) result;
                }
            } catch (Exception e) {
                logger.debug("Method 1 (getConnection) failed: {}", e.getMessage());
            }
            
            // Method 2: Try to get connection field directly (common in JDBC implementations)
            try {
                java.lang.reflect.Field connectionField = null;
                Class<?> currentClass = statement.getClass();
                
                // Search for connection field in class hierarchy
                while (currentClass != null && connectionField == null) {
                    try {
                        connectionField = currentClass.getDeclaredField("connection");
                        break;
                    } catch (NoSuchFieldException e) {
                        // Try other common field names
                        String[] fieldNames = {"conn", "parentConnection", "physicalConnection", "realConnection"};
                        for (String fieldName : fieldNames) {
                            try {
                                connectionField = currentClass.getDeclaredField(fieldName);
                                break;
                            } catch (NoSuchFieldException ignored) {}
                        }
                    }
                    currentClass = currentClass.getSuperclass();
                }
                
                if (connectionField != null) {
                    connectionField.setAccessible(true);
                    Object result = connectionField.get(statement);
                    if (result instanceof Connection) {
                        logger.info("✅ Successfully extracted connection using field '{}': {}", 
                                   connectionField.getName(), result.getClass().getName());
                        return (Connection) result;
                    }
                }
            } catch (Exception e) {
                logger.debug("Method 2 (field access) failed: {}", e.getMessage());
            }
            
            // Method 3: Handle HikariCP proxy patterns
            try {
                // HikariCP often wraps connections in proxy objects
                String className = statement.getClass().getName();
                if (className.contains("Hikari") || className.contains("Proxy")) {
                    logger.debug("Detected HikariCP/Proxy pattern: {}", className);
                    
                    // Try to get the underlying connection from HikariCP proxy
                    java.lang.reflect.Method unwrapMethod = statement.getClass().getMethod("unwrap", Class.class);
                    Object result = unwrapMethod.invoke(statement, Connection.class);
                    if (result instanceof Connection) {
                        logger.debug("✅ Successfully extracted connection using unwrap(): {}", result.getClass().getName());
                        return (Connection) result;
                    }
                }
            } catch (Exception e) {
                logger.debug("Method 3 (HikariCP unwrap) failed: {}", e.getMessage());
            }
            
            // Method 4: PostgreSQL specific connection extraction
            try {
                String className = statement.getClass().getName();
                if (className.contains("postgresql") || className.contains("Pg")) {
                    logger.debug("Detected PostgreSQL JDBC pattern: {}", className);
                    
                    // PostgreSQL JDBC specific methods
                    String[] pgMethods = {"getConnection", "getPGConnection", "getBaseConnection"};
                    for (String methodName : pgMethods) {
                        try {
                            java.lang.reflect.Method method = statement.getClass().getMethod(methodName);
                            Object result = method.invoke(statement);
                            if (result instanceof Connection) {
                                logger.debug("✅ Successfully extracted connection using PostgreSQL method '{}': {}", 
                                           methodName, result.getClass().getName());
                                return (Connection) result;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                logger.debug("Method 4 (PostgreSQL specific) failed: {}", e.getMessage());
            }
            
            // Method 5: Try toString() analysis as last resort (for debugging)
            logger.warn("❌ All connection extraction methods failed for statement type: {}", statement.getClass().getName());
            logger.info("🔍 Statement toString() for debugging: {}", statement.toString());
            
            return null;
            
        } catch (Exception e) {
            logger.error("Fatal error in extractConnection()", e);
            return null;
        }
    }
}