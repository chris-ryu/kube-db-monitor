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
			log.Printf("Client connected. Total clients: %d", len(h.clients))

		case client := <-h.unregister:
			if _, ok := h.clients[client]; ok {
				delete(h.clients, client)
				close(client.send)
				log.Printf("Client disconnected. Total clients: %d", len(h.clients))
			}

		case message := <-h.broadcast:
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

func (h *Hub) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("WebSocket upgrade error: %v", err)
		return
	}

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

// Mock metrics generator for demo
func generateMockMetrics(hub *Hub) {
	queries := []struct {
		pattern    string
		sqlType    string
		tables     []string
		minTime    int64
		maxTime    int64
	}{
		{"SELECT * FROM students WHERE id = ?", "SELECT", []string{"students"}, 5, 20},
		{"INSERT INTO departments (name, code) VALUES (?, ?)", "INSERT", []string{"departments"}, 8, 15},
		{"UPDATE courses SET name = ? WHERE id = ?", "UPDATE", []string{"courses"}, 10, 25},
		{"SELECT s.*, d.name FROM students s JOIN departments d ON s.dept_id = d.id", "SELECT", []string{"students", "departments"}, 15, 45},
		{"DELETE FROM enrollments WHERE student_id = ?", "DELETE", []string{"enrollments"}, 5, 12},
	}

	connectionPool := struct {
		active int
		idle   int
		max    int
	}{5, 3, 10}

	for {
		// Generate random query metrics
		for i, query := range queries {
			if i%3 == 0 { // Generate metrics for some queries
				queryID := time.Now().Format("20060102150405") + string(rune(65+i))
				executionTime := query.minTime + int64(time.Now().UnixNano()%int64(query.maxTime-query.minTime))
				
				// Simulate occasional slow queries or errors
				status := "SUCCESS"
				if executionTime > 40 {
					status = "SUCCESS" // Still success but slow
				}
				if time.Now().UnixNano()%100 < 2 { // 2% error rate
					status = "ERROR"
					executionTime = 0
				}

				// Simulate connection pool changes
				if time.Now().UnixNano()%10 < 3 {
					connectionPool.active = 3 + int(time.Now().UnixNano()%5)
					connectionPool.idle = connectionPool.max - connectionPool.active
				}

				heapUsed := int64(128 + time.Now().UnixNano()%256)
				heapMax := int64(512)
				heapRatio := float64(heapUsed) / float64(heapMax)
				cpuRatio := 0.1 + float64(time.Now().UnixNano()%30)/100

				poolActive := connectionPool.active
				poolIdle := connectionPool.idle
				poolMax := connectionPool.max
				poolRatio := float64(poolActive) / float64(poolMax)

				metrics := QueryMetrics{
					Timestamp: time.Now().Format(time.RFC3339),
					PodName:   "university-registration-demo-abc123",
					Namespace: "kubedb-monitor-test",
					EventType: "query_execution",
					Data: &QueryData{
						QueryID:         queryID,
						SQLPattern:      query.pattern,
						SQLType:         query.sqlType,
						TableNames:      query.tables,
						ExecutionTimeMs: &executionTime,
						Status:          status,
					},
					Metrics: &SystemMetrics{
						ConnectionPoolActive:     &poolActive,
						ConnectionPoolIdle:       &poolIdle,
						ConnectionPoolMax:        &poolMax,
						ConnectionPoolUsageRatio: &poolRatio,
						HeapUsedMb:              &heapUsed,
						HeapMaxMb:               &heapMax,
						HeapUsageRatio:          &heapRatio,
						CPUUsageRatio:           &cpuRatio,
					},
				}

				message := WebSocketMessage{
					Type:      "query_execution",
					Data:      metrics,
					Timestamp: time.Now().Format(time.RFC3339),
				}

				select {
				case hub.broadcast <- message:
				default:
					log.Printf("Hub broadcast channel is full, dropping message")
				}
			}
		}

		// Random sleep between 500ms to 2s
		sleepTime := 500 + time.Now().UnixNano()%1500
		time.Sleep(time.Duration(sleepTime) * time.Millisecond)
	}
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

func main() {
	hub := newHub()
	go hub.run()

	// Start mock metrics generation
	go generateMockMetrics(hub)

	router := mux.NewRouter()
	
	// API routes
	router.HandleFunc("/ws", hub.handleWebSocket)
	router.HandleFunc("/api/health", healthHandler).Methods("GET")
	
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