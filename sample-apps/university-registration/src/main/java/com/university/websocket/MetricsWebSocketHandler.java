package com.university.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;

@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsWebSocketHandler.class);
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        logger.info("WebSocket connection established: {}", session.getId());
        
        // Send welcome message
        Map<String, Object> welcomeMessage = Map.of(
            "type", "connection_established",
            "sessionId", session.getId(),
            "timestamp", System.currentTimeMillis()
        );
        
        sendMessage(session, welcomeMessage);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        logger.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
    }
    
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.debug("Received message from {}: {}", session.getId(), message.getPayload());
        // Handle incoming messages if needed
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session {}: ", session.getId(), exception);
        sessions.remove(session.getId());
    }
    
    public void broadcastMessage(Object message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            logger.error("Failed to serialize message: ", e);
            return;
        }
        
        sessions.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            if (!session.isOpen()) {
                return true;
            }
            
            try {
                session.sendMessage(new TextMessage(jsonMessage));
                return false;
            } catch (IOException e) {
                logger.error("Failed to send message to session {}: ", entry.getKey(), e);
                return true;
            }
        });
    }
    
    private void sendMessage(WebSocketSession session, Object message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
        } catch (Exception e) {
            logger.error("Failed to send message to session {}: ", session.getId(), e);
        }
    }
    
    public int getActiveSessionsCount() {
        return sessions.size();
    }
}