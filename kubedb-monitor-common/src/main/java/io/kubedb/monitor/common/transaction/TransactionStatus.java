package io.kubedb.monitor.common.transaction;

/**
 * Transaction status enumeration
 */
public enum TransactionStatus {
    ACTIVE,
    COMMITTED,
    ROLLED_BACK,
    UNKNOWN
}