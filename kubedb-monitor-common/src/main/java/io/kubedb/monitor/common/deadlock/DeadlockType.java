package io.kubedb.monitor.common.deadlock;

/**
 * Types of deadlocks that can be detected
 */
public enum DeadlockType {
    CIRCULAR_WAIT,      // Standard circular wait deadlock
    WAIT_FOR_GRAPH,     // Wait-for graph based deadlock
    TIMEOUT_BASED,      // Detected via lock timeout
    UNKNOWN
}