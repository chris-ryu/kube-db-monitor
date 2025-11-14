package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/gorilla/mux"
	"github.com/gorilla/websocket"
	"github.com/rs/cors"
)

type QueryMetrics struct {
	Timestamp string                 `json:"timestamp"`
	PodName   string                 `json:"pod_name,omitempty"`
	Namespace string                 `json:"namespace,omitempty"`
	EventType string                 `json:"event_type"`
	Data      *QueryData             `json:"data,omitempty"`
	Context   *ExecutionContext      `json:"context,omitempty"`
	Metrics   *SystemMetrics         `json:"metrics,omitempty"`
}

type QueryData struct {
	QueryID           string            `json:"query_id"`
	SQLHash           string            `json:"sql_hash,omitempty"`
	SQLPattern        string            `json:"sql_pattern,omitempty"`
	SQLType           string            `json:"sql_type,omitempty"`
	TableNames        []string          `json:"table_names,omitempty"`
	ExecutionTimeMs   *int64            `json:"execution_time_ms,omitempty"`
	RowsAffected      *int64            `json:"rows_affected,omitempty"`
	ConnectionID      string            `json:"connection_id,omitempty"`
	ThreadName        string            `json:"thread_name,omitempty"`
	MemoryUsedBytes   *int64            `json:"memory_used_bytes,omitempty"`
	Status            string            `json:"status"`
	ErrorMessage      string            `json:"error_message,omitempty"`
	ComplexityScore   *int              `json:"complexity_score,omitempty"`
	CacheHitRatio     *float64          `json:"cache_hit_ratio,omitempty"`
	
	// Additional fields for advanced events
	TpsValue              *float64 `json:"tps_value,omitempty"`              // For TPS events
	TransactionDuration   *int64   `json:"transaction_duration,omitempty"`   // For long running transaction events
	TransactionId         *string  `json:"transaction_id,omitempty"`         // For transaction events
	DeadlockDuration      *int64   `json:"deadlock_duration,omitempty"`      // For deadlock events
	DeadlockConnections   *string  `json:"deadlock_connections,omitempty"`   // For deadlock events
	
	// Long running transaction query information
	CurrentQuery      *string                  `json:"current_query,omitempty"`       // Currently executing query
	StoredProcedure   *string                  `json:"stored_procedure,omitempty"`    // Stored procedure name
	QueryHistory      []QueryHistoryInfo       `json:"query_history,omitempty"`       // Query execution history
}

type QueryHistoryInfo struct {
	Query         string `json:"query"`
	StartTime     int64  `json:"start_time"`
	ExecutionTime int64  `json:"execution_time"`
	QueryType     string `json:"query_type"`
}

type ExecutionContext struct {
	RequestID         string `json:"request_id,omitempty"`
	UserSession       string `json:"user_session,omitempty"`
	APIEndpoint       string `json:"api_endpoint,omitempty"`
	BusinessOperation string `json:"business_operation,omitempty"`
	UserID            string `json:"user_id,omitempty"`
}

type SystemMetrics struct {
	ConnectionPoolActive      *int     `json:"connection_pool_active,omitempty"`
	ConnectionPoolIdle        *int     `json:"connection_pool_idle,omitempty"`
	ConnectionPoolMax         *int     `json:"connection_pool_max,omitempty"`
	ConnectionPoolUsageRatio  *float64 `json:"connection_pool_usage_ratio,omitempty"`
	
	// 고급 Connection Pool 메트릭 추가
	ConnectionPoolPeakActive         *int     `json:"connection_pool_peak_active,omitempty"`
	ConnectionPoolPeakTimestamp      *int64   `json:"connection_pool_peak_timestamp,omitempty"`
	ConnectionPoolRequestsPerSecond  *int     `json:"connection_pool_requests_per_second,omitempty"`
	ConnectionPoolHealthScore        *int     `json:"connection_pool_health_score,omitempty"`
	ConnectionPoolAverageHoldTime    *float64 `json:"connection_pool_average_hold_time,omitempty"`
	ConnectionPoolWaitingThreads     *int     `json:"connection_pool_waiting_threads,omitempty"`
	
	HeapUsedMb               *int64   `json:"heap_used_mb,omitempty"`
	HeapMaxMb                *int64   `json:"heap_max_mb,omitempty"`
	HeapUsageRatio           *float64 `json:"heap_usage_ratio,omitempty"`
	CPUUsageRatio            *float64 `json:"cpu_usage_ratio,omitempty"`
	GCCount                  *int64   `json:"gc_count,omitempty"`
	GCTimeMs                 *int64   `json:"gc_time_ms,omitempty"`
}

type WebSocketMessage struct {
	Type      string      `json:"type"`
	Data      interface{} `json:"data"`
	Timestamp string      `json:"timestamp"`
}

// MonitoringSession represents a recorded monitoring session
type MonitoringSession struct {
	ID              string        `json:"id"`
	SessionName     string        `json:"session_name"`
	Description     string        `json:"description,omitempty"`
	StartTime       string        `json:"start_time"`
	EndTime         string        `json:"end_time,omitempty"`
	Status          string        `json:"status"` // recording, completed
	DurationMinutes int           `json:"duration_minutes,omitempty"`
	MetricsData     []interface{} `json:"metrics_data"`
	TransactionsData []interface{} `json:"transactions_data"`
	CreatedAt       time.Time     `json:"created_at"`
	UpdatedAt       time.Time     `json:"updated_at"`
}

// SessionStorage handles persistent storage of monitoring sessions
type SessionStorage struct {
	basePath string
}

// NewSessionStorage creates a new session storage instance
func NewSessionStorage(basePath string) *SessionStorage {
	// Create directory if it doesn't exist
	if err := os.MkdirAll(basePath, 0755); err != nil {
		log.Printf("❌ Failed to create sessions directory: %v", err)
		return nil
	}
	
	return &SessionStorage{
		basePath: basePath,
	}
}

// Create saves a new monitoring session
func (s *SessionStorage) Create(session *MonitoringSession) error {
	session.CreatedAt = time.Now()
	session.UpdatedAt = time.Now()
	
	if session.ID == "" {
		session.ID = fmt.Sprintf("session-%d", time.Now().UnixNano())
	}
	
	return s.save(session)
}

// Update modifies an existing monitoring session
func (s *SessionStorage) Update(id string, updates map[string]interface{}) error {
	session, err := s.GetByID(id)
	if err != nil {
		return err
	}
	
	// Apply updates
	if sessionName, ok := updates["session_name"].(string); ok {
		session.SessionName = sessionName
	}
	if description, ok := updates["description"].(string); ok {
		session.Description = description
	}
	if endTime, ok := updates["end_time"].(string); ok {
		session.EndTime = endTime
	}
	if status, ok := updates["status"].(string); ok {
		session.Status = status
	}
	if durationMinutes, ok := updates["duration_minutes"].(int); ok {
		session.DurationMinutes = durationMinutes
	}
	if metricsData, ok := updates["metrics_data"].([]interface{}); ok {
		session.MetricsData = metricsData
	}
	if transactionsData, ok := updates["transactions_data"].([]interface{}); ok {
		session.TransactionsData = transactionsData
	}
	
	session.UpdatedAt = time.Now()
	return s.save(session)
}

// GetByID retrieves a session by its ID
func (s *SessionStorage) GetByID(id string) (*MonitoringSession, error) {
	filePath := filepath.Join(s.basePath, id+".json")
	
	data, err := ioutil.ReadFile(filePath)
	if err != nil {
		return nil, fmt.Errorf("session not found: %s", id)
	}
	
	var session MonitoringSession
	if err := json.Unmarshal(data, &session); err != nil {
		return nil, fmt.Errorf("failed to parse session: %v", err)
	}
	
	return &session, nil
}

// List retrieves all sessions with optional sorting
func (s *SessionStorage) List(sortBy string, limit int) ([]*MonitoringSession, error) {
	files, err := ioutil.ReadDir(s.basePath)
	if err != nil {
		return nil, fmt.Errorf("failed to read sessions directory: %v", err)
	}
	
	var sessions []*MonitoringSession
	
	for _, file := range files {
		if !strings.HasSuffix(file.Name(), ".json") {
			continue
		}
		
		id := strings.TrimSuffix(file.Name(), ".json")
		session, err := s.GetByID(id)
		if err != nil {
			log.Printf("⚠️ Failed to load session %s: %v", id, err)
			continue
		}
		
		sessions = append(sessions, session)
	}
	
	// Sort sessions
	switch sortBy {
	case "-created_date":
		sort.Slice(sessions, func(i, j int) bool {
			return sessions[i].CreatedAt.After(sessions[j].CreatedAt)
		})
	case "-updated_date":
		sort.Slice(sessions, func(i, j int) bool {
			return sessions[i].UpdatedAt.After(sessions[j].UpdatedAt)
		})
	default:
		// Default sort by created date descending
		sort.Slice(sessions, func(i, j int) bool {
			return sessions[i].CreatedAt.After(sessions[j].CreatedAt)
		})
	}
	
	// Apply limit
	if limit > 0 && len(sessions) > limit {
		sessions = sessions[:limit]
	}
	
	return sessions, nil
}

// Delete removes a session
func (s *SessionStorage) Delete(id string) error {
	filePath := filepath.Join(s.basePath, id+".json")
	
	if err := os.Remove(filePath); err != nil {
		return fmt.Errorf("failed to delete session %s: %v", id, err)
	}
	
	log.Printf("✅ Deleted session: %s", id)
	return nil
}

// save persists a session to disk
func (s *SessionStorage) save(session *MonitoringSession) error {
	filePath := filepath.Join(s.basePath, session.ID+".json")
	
	data, err := json.MarshalIndent(session, "", "  ")
	if err != nil {
		return fmt.Errorf("failed to marshal session: %v", err)
	}
	
	if err := ioutil.WriteFile(filePath, data, 0644); err != nil {
		return fmt.Errorf("failed to write session file: %v", err)
	}
	
	log.Printf("💾 Saved session: %s", session.ID)
	return nil
}

// CleanupOldSessions removes sessions older than specified days
func (s *SessionStorage) CleanupOldSessions(retentionDays int) error {
	cutoff := time.Now().AddDate(0, 0, -retentionDays)
	
	sessions, err := s.List("", 0)
	if err != nil {
		return err
	}
	
	deletedCount := 0
	for _, session := range sessions {
		if session.CreatedAt.Before(cutoff) {
			if err := s.Delete(session.ID); err != nil {
				log.Printf("⚠️ Failed to cleanup session %s: %v", session.ID, err)
			} else {
				deletedCount++
			}
		}
	}
	
	if deletedCount > 0 {
		log.Printf("🧹 Cleaned up %d old sessions", deletedCount)
	}
	
	return nil
}

type Hub struct {
	clients    map[*Client]bool
	broadcast  chan WebSocketMessage
	register   chan *Client
	unregister chan *Client
}

// createDeadlockMessage creates a dashboard-compatible deadlock message
func createDeadlockMessage(metric QueryMetrics) WebSocketMessage {
	// Extract connection information from deadlock_connections field
	connections := ""
	if metric.Data.DeadlockConnections != nil {
		connections = *metric.Data.DeadlockConnections
	}
	
	// Parse connections to create participants
	participants := parseConnectionsToParticipants(connections)
	
	// Create deadlock event data in the format expected by dashboard
	// Include pod name and transaction ID in the unique identifier to avoid duplicates
	uniqueId := fmt.Sprintf("deadlock-%s-%d", 
		strings.ReplaceAll(metric.PodName, "-", ""), 
		time.Now().UnixNano())
	if metric.Data != nil && metric.Data.TransactionId != nil {
		uniqueId = fmt.Sprintf("deadlock-%s-%s-%d", 
			strings.ReplaceAll(metric.PodName, "-", ""),
			strings.ReplaceAll(*metric.Data.TransactionId, "-", ""),
			time.Now().UnixNano())
	}
	
	deadlockData := map[string]interface{}{
		"id":             uniqueId,
		"participants":   participants,
		"detectionTime":  time.Now().Format(time.RFC3339),
		"recommendedVictim": "connection-1",
		"lockChain":      createLockChain(participants),
		"severity":       "critical",
		"status":         "active",
		"pod_name":       metric.PodName,
		"namespace":      "production",
		"cycleLength":    len(participants),
		"duration_ms":    metric.Data.DeadlockDuration,
		"connections":    connections,
	}
	
	return WebSocketMessage{
		Type:      "deadlock_event",
		Data:      deadlockData,
		Timestamp: time.Now().Format(time.RFC3339),
	}
}

func parseConnectionsToParticipants(connections string) []map[string]interface{} {
	if connections == "" {
		return []map[string]interface{}{
			{"id": "connection-1", "resource": "table_unknown", "lockType": "exclusive"},
			{"id": "connection-2", "resource": "table_unknown", "lockType": "shared"},
		}
	}
	
	// Parse "PgConnection@ac889df:PgConnection@139539a4" format
	parts := strings.Split(connections, ":")
	participants := make([]map[string]interface{}, 0, len(parts))
	
	for i, part := range parts {
		if strings.TrimSpace(part) != "" {
			lockType := "shared"
			if i%2 == 0 {
				lockType = "exclusive"
			}
			participants = append(participants, map[string]interface{}{
				"id":         fmt.Sprintf("connection-%d", i+1),
				"resource":   fmt.Sprintf("table_%d", i+1),
				"lockType":   lockType,
				"connection": strings.TrimSpace(part),
			})
		}
	}
	
	if len(participants) == 0 {
		participants = []map[string]interface{}{
			{"id": "connection-1", "resource": "table_1", "lockType": "exclusive"},
			{"id": "connection-2", "resource": "table_2", "lockType": "shared"},
		}
	}
	
	return participants
}

func createLockChain(participants []map[string]interface{}) []string {
	lockChain := make([]string, 0, len(participants))
	
	for i, participant := range participants {
		nextIndex := (i + 1) % len(participants)
		from := fmt.Sprintf("%v", participant["id"])
		to := fmt.Sprintf("%v", participants[nextIndex]["id"])
		resource := fmt.Sprintf("%v", participant["resource"])
		lockType := fmt.Sprintf("%v", participant["lockType"])
		
		lockDescription := fmt.Sprintf("%s → %s (%s, %s)", from, to, resource, lockType)
		lockChain = append(lockChain, lockDescription)
	}
	
	return lockChain
}

type Client struct {
	hub  *Hub
	conn *websocket.Conn
	send chan WebSocketMessage
}

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true // Allow all origins for demo
	},
}

func newHub() *Hub {
	return &Hub{
		broadcast:  make(chan WebSocketMessage, 256),
		register:   make(chan *Client),
		unregister: make(chan *Client),
		clients:    make(map[*Client]bool),
	}
}

func (h *Hub) run() {
	for {
		select {
		case client := <-h.register:
			h.clients[client] = true
			log.Printf("✅ Client connected. Total clients: %d", len(h.clients))

		case client := <-h.unregister:
			if _, ok := h.clients[client]; ok {
				delete(h.clients, client)
				close(client.send)
				log.Printf("🔌 Client disconnected. Total clients: %d", len(h.clients))
			}

		case message := <-h.broadcast:
			log.Printf("📡 Broadcasting message to %d clients", len(h.clients))
			for client := range h.clients {
				select {
				case client.send <- message:
				default:
					close(client.send)
					delete(h.clients, client)
				}
			}
		}
	}
}

func (h *Hub) receiveMetrics(w http.ResponseWriter, r *http.Request) {
	var metric QueryMetrics
	if err := json.NewDecoder(r.Body).Decode(&metric); err != nil {
		log.Printf("❌ Failed to decode metrics: %v", err)
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}

	// Extract Pod and Namespace information from the request and JSON payload
	// First try to get from the JSON payload itself (Agent sends these in the payload)
	if metric.PodName == "" {
		podName := extractPodNameFromRequest(r)
		if podName != "" {
			metric.PodName = podName
		}
	}
	
	if metric.Namespace == "" {
		namespace := extractNamespaceFromRequest(r)
		if namespace != "" {
			metric.Namespace = namespace
		}
	}

	// Safe logging to avoid panic
	sqlType := "unknown"
	if metric.Data != nil {
		sqlType = metric.Data.SQLType
	}
	log.Printf("📊 Received real JDBC metric: %s - %s from Pod: %s, Namespace: %s", 
		metric.EventType, sqlType, metric.PodName, metric.Namespace)
	
	// Broadcast the real metric to all connected WebSocket clients with proper type
	var messageType string
	switch metric.EventType {
	case "query_execution":
		messageType = "query_metrics"
	case "transaction_event":
		messageType = "transaction_event"
	case "deadlock_event":
		messageType = "deadlock_event"
	case "deadlock_detected":
		messageType = "deadlock_event"
		log.Printf("💀 Converting deadlock_detected to deadlock_event for WebSocket broadcast")
		
		// Create special deadlock message with dashboard-compatible structure
		deadlockMessage := createDeadlockMessage(metric)
		h.broadcast <- deadlockMessage
		
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"status": "received"})
		return // Early return for deadlock events
	case "long_running_transaction":
		messageType = "long_running_transaction"
		log.Printf("🐌 Processing long_running_transaction event for WebSocket broadcast")
		log.Printf("🔍 DEBUG: Full long_running_transaction message: %+v", metric)
		if metric.Data != nil {
			log.Printf("🔍 DEBUG: Long running transaction data: %+v", *metric.Data)
		}
	default:
		messageType = "query_metrics" // default fallback
	}
	
	message := WebSocketMessage{
		Type: messageType,
		Data: metric,
		Timestamp: time.Now().Format(time.RFC3339),
	}

	h.broadcast <- message
	
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"status": "received"})
}

func (h *Hub) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	log.Printf("🔗 WebSocket connection attempt from %s", r.RemoteAddr)
	log.Printf("🔍 Headers: %+v", r.Header)
	
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("❌ WebSocket upgrade error: %v", err)
		return
	}
	
	log.Printf("✅ WebSocket upgrade successful")

	client := &Client{
		hub:  h,
		conn: conn,
		send: make(chan WebSocketMessage, 256),
	}

	client.hub.register <- client

	go client.writePump()
	go client.readPump()
}

func (c *Client) readPump() {
	defer func() {
		c.hub.unregister <- c
		c.conn.Close()
	}()

	c.conn.SetReadLimit(512)
	c.conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		return nil
	})

	for {
		_, _, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				log.Printf("WebSocket error: %v", err)
			}
			break
		}
	}
}

func (c *Client) writePump() {
	ticker := time.NewTicker(54 * time.Second)
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if !ok {
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			if err := c.conn.WriteJSON(message); err != nil {
				log.Printf("WebSocket write error: %v", err)
				return
			}

		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// Mock metrics generator removed - using real JDBC data from /api/metrics endpoint

// Helper functions to extract Pod and Namespace information
func extractPodNameFromRequest(r *http.Request) string {
	// Try to get from environment variables (set in Kubernetes deployment)
	// If the Agent is running in the same pod as the application, we can use these
	podName := os.Getenv("HOSTNAME") // Kubernetes sets HOSTNAME to pod name
	if podName != "" {
		return podName
	}
	
	// Try to extract from User-Agent or other headers if Agent sends it
	userAgent := r.Header.Get("User-Agent")
	if strings.Contains(userAgent, "pod:") {
		parts := strings.Split(userAgent, "pod:")
		if len(parts) > 1 {
			return strings.TrimSpace(strings.Split(parts[1], " ")[0])
		}
	}
	
	// If we can't determine the pod name from the Agent request,
	// we'll need to look at the source IP and match it to known pods
	// For now, return a default based on known University Registration pod pattern
	return "university-registration-demo"  // This should be made more dynamic
}

func extractNamespaceFromRequest(r *http.Request) string {
	// Try to get from environment variables
	namespace := os.Getenv("NAMESPACE")
	if namespace != "" {
		return namespace
	}
	
	// Try to extract from headers if Agent sends it
	userAgent := r.Header.Get("User-Agent")
	if strings.Contains(userAgent, "namespace:") {
		parts := strings.Split(userAgent, "namespace:")
		if len(parts) > 1 {
			return strings.TrimSpace(strings.Split(parts[1], " ")[0])
		}
	}
	
	// Default to the namespace where University Registration runs
	return "kubedb-monitor-test"
}

func healthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{
		"status":    "healthy",
		"service":   "kubedb-monitor-control-plane",
		"timestamp": time.Now().Format(time.RFC3339),
	})
}

// Session API Handlers

func handleCreateSession(storage *SessionStorage) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var session MonitoringSession
		if err := json.NewDecoder(r.Body).Decode(&session); err != nil {
			http.Error(w, "Invalid JSON", http.StatusBadRequest)
			return
		}
		
		// Validate required fields
		if session.SessionName == "" {
			http.Error(w, "Session name is required", http.StatusBadRequest)
			return
		}
		
		if err := storage.Create(&session); err != nil {
			log.Printf("❌ Failed to create session: %v", err)
			http.Error(w, "Failed to create session", http.StatusInternalServerError)
			return
		}
		
		log.Printf("✅ Created new session: %s", session.SessionName)
		
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		json.NewEncoder(w).Encode(session)
	}
}

func handleListSessions(storage *SessionStorage) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		sortBy := r.URL.Query().Get("sort")
		if sortBy == "" {
			sortBy = "-created_date"
		}
		
		limitStr := r.URL.Query().Get("limit")
		limit := 0
		if limitStr != "" {
			if parsedLimit, err := strconv.Atoi(limitStr); err == nil {
				limit = parsedLimit
			}
		}
		
		sessions, err := storage.List(sortBy, limit)
		if err != nil {
			log.Printf("❌ Failed to list sessions: %v", err)
			http.Error(w, "Failed to list sessions", http.StatusInternalServerError)
			return
		}
		
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(sessions)
	}
}

func handleGetSession(storage *SessionStorage) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		vars := mux.Vars(r)
		id := vars["id"]
		
		session, err := storage.GetByID(id)
		if err != nil {
			if strings.Contains(err.Error(), "not found") {
				http.Error(w, "Session not found", http.StatusNotFound)
			} else {
				log.Printf("❌ Failed to get session %s: %v", id, err)
				http.Error(w, "Failed to get session", http.StatusInternalServerError)
			}
			return
		}
		
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(session)
	}
}

func handleUpdateSession(storage *SessionStorage) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		vars := mux.Vars(r)
		id := vars["id"]
		
		var updates map[string]interface{}
		if err := json.NewDecoder(r.Body).Decode(&updates); err != nil {
			http.Error(w, "Invalid JSON", http.StatusBadRequest)
			return
		}
		
		if err := storage.Update(id, updates); err != nil {
			if strings.Contains(err.Error(), "not found") {
				http.Error(w, "Session not found", http.StatusNotFound)
			} else {
				log.Printf("❌ Failed to update session %s: %v", id, err)
				http.Error(w, "Failed to update session", http.StatusInternalServerError)
			}
			return
		}
		
		// Return updated session
		session, err := storage.GetByID(id)
		if err != nil {
			log.Printf("❌ Failed to get updated session %s: %v", id, err)
			http.Error(w, "Session updated but failed to retrieve", http.StatusInternalServerError)
			return
		}
		
		log.Printf("✅ Updated session: %s", id)
		
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(session)
	}
}

func handleDeleteSession(storage *SessionStorage) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		vars := mux.Vars(r)
		id := vars["id"]
		
		if err := storage.Delete(id); err != nil {
			if strings.Contains(err.Error(), "no such file") {
				http.Error(w, "Session not found", http.StatusNotFound)
			} else {
				log.Printf("❌ Failed to delete session %s: %v", id, err)
				http.Error(w, "Failed to delete session", http.StatusInternalServerError)
			}
			return
		}
		
		log.Printf("✅ Deleted session: %s", id)
		
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{
			"message": "Session deleted successfully",
			"id":      id,
		})
	}
}

func main() {
	log.Printf("🎉 KubeDB Monitor Control Plane starting...")
	
	// Get port from environment variable, default to 8080
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	
	// Initialize session storage
	sessionsPath := os.Getenv("SESSIONS_STORAGE_PATH")
	if sessionsPath == "" {
		sessionsPath = "/tmp/sessions" // fallback for local development
	}
	
	sessionStorage := NewSessionStorage(sessionsPath)
	if sessionStorage == nil {
		log.Fatalf("❌ Failed to initialize session storage")
	}
	
	log.Printf("💾 Session storage initialized at: %s", sessionsPath)
	
	// Start cleanup routine for old sessions
	go func() {
		ticker := time.NewTicker(24 * time.Hour) // Run daily
		defer ticker.Stop()
		
		for {
			select {
			case <-ticker.C:
				if err := sessionStorage.CleanupOldSessions(30); err != nil {
					log.Printf("⚠️ Session cleanup failed: %v", err)
				}
			}
		}
	}()
	
	hub := newHub()
	go hub.run()

	// Mock metrics generation disabled - using real JDBC data from /api/metrics endpoint

	router := mux.NewRouter()
	
	// API routes
	router.HandleFunc("/ws", hub.handleWebSocket)
	router.HandleFunc("/api/health", healthHandler).Methods("GET")
	router.HandleFunc("/api/metrics", hub.receiveMetrics).Methods("POST")
	
	// Session management routes
	router.HandleFunc("/api/sessions", handleCreateSession(sessionStorage)).Methods("POST")
	router.HandleFunc("/api/sessions", handleListSessions(sessionStorage)).Methods("GET")
	router.HandleFunc("/api/sessions/{id}", handleGetSession(sessionStorage)).Methods("GET")
	router.HandleFunc("/api/sessions/{id}", handleUpdateSession(sessionStorage)).Methods("PUT")
	router.HandleFunc("/api/sessions/{id}", handleDeleteSession(sessionStorage)).Methods("DELETE")
	
	// Serve static files for dashboard (if needed)
	router.PathPrefix("/").Handler(http.FileServer(http.Dir("./static/")))

	// CORS middleware
	c := cors.New(cors.Options{
		AllowedOrigins: []string{"*"}, // Allow all origins for demo
		AllowedMethods: []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders: []string{"*"},
	})

	handler := c.Handler(router)

	server := &http.Server{
		Addr:         ":" + port,
		Handler:      handler,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
	}

	// Graceful shutdown
	go func() {
		log.Printf("KubeDB Monitor Control Plane starting on :%s", port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server failed to start: %v", err)
		}
	}()

	// Wait for interrupt signal
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
	<-sigChan

	log.Println("Shutting down server...")

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced to shutdown: %v", err)
	}

	log.Println("Server gracefully stopped")
}