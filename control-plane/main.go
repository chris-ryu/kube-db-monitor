package main

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"os/signal"
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
	HeapUsedMb               *int64   `json:"heap_used_mb,omitempty"`
	HeapMaxMb                *int64   `json:"heap_max_mb,omitempty"`
	HeapUsageRatio           *float64 `json:"heap_usage_ratio,omitempty"`
	CPUUsageRatio            *float64 `json:"cpu_usage_ratio,omitempty"`
	GCCount                  *int64   `json:"gc_count,omitempty"`
	GCTimeMs                 *int64   `json:"gc_time_ms,omitempty"`
}

type WebSocketMessage struct {
	Type      string       `json:"type"`
	Data      QueryMetrics `json:"data"`
	Timestamp string       `json:"timestamp"`
}

type Hub struct {
	clients    map[*Client]bool
	broadcast  chan WebSocketMessage
	register   chan *Client
	unregister chan *Client
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

	// Safe logging to avoid panic
	sqlType := "unknown"
	if metric.Data != nil {
		sqlType = metric.Data.SQLType
	}
	log.Printf("📊 Received real JDBC metric: %s - %s", metric.EventType, sqlType)
	
	// Broadcast the real metric to all connected WebSocket clients with proper type
	var messageType string
	switch metric.EventType {
	case "query_execution":
		messageType = "query_metrics"
	case "transaction_event":
		messageType = "transaction_event"
	case "deadlock_event":
		messageType = "deadlock_event"
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

func healthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{
		"status":    "healthy",
		"service":   "kubedb-monitor-control-plane",
		"timestamp": time.Now().Format(time.RFC3339),
	})
}

func main() {
	log.Printf("🎉 KubeDB Monitor Control Plane starting...")
	
	hub := newHub()
	go hub.run()

	// Mock metrics generation disabled - using real JDBC data from /api/metrics endpoint

	router := mux.NewRouter()
	
	// API routes
	router.HandleFunc("/ws", hub.handleWebSocket)
	router.HandleFunc("/api/health", healthHandler).Methods("GET")
	router.HandleFunc("/api/metrics", hub.receiveMetrics).Methods("POST")
	
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
		Addr:         ":8080",
		Handler:      handler,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
	}

	// Graceful shutdown
	go func() {
		log.Println("KubeDB Monitor Control Plane starting on :8080")
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