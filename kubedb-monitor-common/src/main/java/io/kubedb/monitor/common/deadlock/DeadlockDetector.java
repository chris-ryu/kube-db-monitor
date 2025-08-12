package io.kubedb.monitor.common.deadlock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects deadlocks using wait-for graph analysis
 */
public class DeadlockDetector {
    private static final Logger logger = LoggerFactory.getLogger(DeadlockDetector.class);
    
    // Transaction -> Resources it's waiting for
    private final Map<String, Set<String>> transactionWaits = new ConcurrentHashMap<>();
    
    // Transaction -> Resources it currently holds
    private final Map<String, Set<String>> transactionHolds = new ConcurrentHashMap<>();
    
    // Resource -> Transaction that holds it
    private final Map<String, String> resourceOwners = new ConcurrentHashMap<>();
    
    // Transaction -> Cost of rollback (for victim selection)
    private final Map<String, Long> transactionCosts = new ConcurrentHashMap<>();
    
    /**
     * Register that a transaction is requesting a lock on a resource
     */
    public void registerLockRequest(String transactionId, String resourceId) {
        logger.debug("Transaction {} requesting lock on {}", transactionId, resourceId);
        
        transactionWaits.computeIfAbsent(transactionId, k -> ConcurrentHashMap.newKeySet())
                       .add(resourceId);
    }
    
    /**
     * Register that a transaction has acquired a lock on a resource
     */
    public void registerLockAcquired(String transactionId, String resourceId) {
        logger.debug("Transaction {} acquired lock on {}", transactionId, resourceId);
        
        // Remove from waiting list if it was there
        Set<String> waiting = transactionWaits.get(transactionId);
        if (waiting != null) {
            waiting.remove(resourceId);
        }
        
        // Add to held resources
        transactionHolds.computeIfAbsent(transactionId, k -> ConcurrentHashMap.newKeySet())
                       .add(resourceId);
        
        // Record ownership
        resourceOwners.put(resourceId, transactionId);
    }
    
    /**
     * Register that a transaction has released a lock on a resource
     */
    public void registerLockReleased(String transactionId, String resourceId) {
        logger.debug("Transaction {} released lock on {}", transactionId, resourceId);
        
        // Remove from held resources
        Set<String> held = transactionHolds.get(transactionId);
        if (held != null) {
            held.remove(resourceId);
        }
        
        // Remove ownership
        resourceOwners.remove(resourceId);
    }
    
    /**
     * Register transaction cost for victim selection
     */
    public void registerTransactionCost(String transactionId, long cost) {
        transactionCosts.put(transactionId, cost);
    }
    
    /**
     * Check for deadlocks using cycle detection in wait-for graph
     */
    public Optional<DeadlockEvent> checkForDeadlock() {
        // Build wait-for graph: Transaction -> Transaction it's waiting for
        Map<String, Set<String>> waitForGraph = buildWaitForGraph();
        
        // Find cycles in the graph
        Optional<List<String>> cycle = findCycle(waitForGraph);
        
        if (cycle.isPresent()) {
            List<String> participants = cycle.get();
            String victim = selectVictim(participants);
            
            return Optional.of(new DeadlockEvent(
                new HashSet<>(participants),
                DeadlockType.CIRCULAR_WAIT,
                victim,
                participants
            ));
        }
        
        return Optional.empty();
    }
    
    /**
     * Build wait-for graph: Transaction A waits for Transaction B if A wants a resource held by B
     */
    private Map<String, Set<String>> buildWaitForGraph() {
        Map<String, Set<String>> graph = new HashMap<>();
        
        for (Map.Entry<String, Set<String>> entry : transactionWaits.entrySet()) {
            String waitingTransaction = entry.getKey();
            Set<String> wantedResources = entry.getValue();
            
            for (String resource : wantedResources) {
                String ownerTransaction = resourceOwners.get(resource);
                if (ownerTransaction != null && !ownerTransaction.equals(waitingTransaction)) {
                    graph.computeIfAbsent(waitingTransaction, k -> new HashSet<>())
                         .add(ownerTransaction);
                }
            }
        }
        
        return graph;
    }
    
    /**
     * Find a cycle in the wait-for graph using DFS
     */
    private Optional<List<String>> findCycle(Map<String, Set<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String transaction : graph.keySet()) {
            if (!visited.contains(transaction)) {
                List<String> cycle = dfsForCycle(graph, transaction, visited, recursionStack, new ArrayList<>());
                if (cycle != null) {
                    return Optional.of(cycle);
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * DFS to detect cycle and return the cycle path
     */
    private List<String> dfsForCycle(Map<String, Set<String>> graph, String current, 
                                    Set<String> visited, Set<String> recursionStack, 
                                    List<String> path) {
        visited.add(current);
        recursionStack.add(current);
        path.add(current);
        
        Set<String> neighbors = graph.get(current);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    List<String> result = dfsForCycle(graph, neighbor, visited, recursionStack, new ArrayList<>(path));
                    if (result != null) {
                        return result;
                    }
                } else if (recursionStack.contains(neighbor)) {
                    // Found cycle - return the cycle path
                    List<String> cycle = new ArrayList<>(path);
                    cycle.add(neighbor);
                    return cycle;
                }
            }
        }
        
        recursionStack.remove(current);
        return null;
    }
    
    /**
     * Select deadlock victim - transaction with lowest cost
     */
    private String selectVictim(List<String> participants) {
        return participants.stream()
                .min(Comparator.comparingLong(tx -> transactionCosts.getOrDefault(tx, 0L)))
                .orElse(participants.get(0));
    }
    
    /**
     * Check if transaction is active
     */
    public boolean isTransactionActive(String transactionId) {
        return transactionWaits.containsKey(transactionId) || 
               transactionHolds.containsKey(transactionId);
    }
    
    /**
     * Get resources a transaction is waiting for
     */
    public Set<String> getResourcesWaitingFor(String transactionId) {
        return new HashSet<>(transactionWaits.getOrDefault(transactionId, Collections.emptySet()));
    }
    
    /**
     * Get resources held by a transaction
     */
    public Set<String> getResourcesHeldBy(String transactionId) {
        return new HashSet<>(transactionHolds.getOrDefault(transactionId, Collections.emptySet()));
    }
    
    /**
     * Clean up when transaction completes
     */
    public void onTransactionCompleted(String transactionId) {
        logger.debug("Cleaning up completed transaction: {}", transactionId);
        
        // Release all held resources
        Set<String> heldResources = transactionHolds.remove(transactionId);
        if (heldResources != null) {
            for (String resource : heldResources) {
                resourceOwners.remove(resource);
            }
        }
        
        // Remove from waiting lists
        transactionWaits.remove(transactionId);
        transactionCosts.remove(transactionId);
    }
}