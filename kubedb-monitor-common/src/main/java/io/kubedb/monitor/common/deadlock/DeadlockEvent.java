package io.kubedb.monitor.common.deadlock;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Represents a detected deadlock event
 */
public class DeadlockEvent {
    private final Set<String> participants;
    private final DeadlockType deadlockType;
    private final String recommendedVictim;
    private final Instant detectionTime;
    private final List<String> lockChain;
    
    public DeadlockEvent(Set<String> participants, DeadlockType deadlockType, 
                        String recommendedVictim, List<String> lockChain) {
        this.participants = participants;
        this.deadlockType = deadlockType;
        this.recommendedVictim = recommendedVictim;
        this.lockChain = lockChain;
        this.detectionTime = Instant.now();
    }
    
    public Set<String> getParticipants() {
        return participants;
    }
    
    public DeadlockType getDeadlockType() {
        return deadlockType;
    }
    
    public String getRecommendedVictim() {
        return recommendedVictim;
    }
    
    public Instant getDetectionTime() {
        return detectionTime;
    }
    
    public List<String> getLockChain() {
        return lockChain;
    }
    
    @Override
    public String toString() {
        return "DeadlockEvent{" +
                "participants=" + participants +
                ", type=" + deadlockType +
                ", recommendedVictim='" + recommendedVictim + '\'' +
                ", detectionTime=" + detectionTime +
                '}';
    }
}