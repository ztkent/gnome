package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"

	"github.com/Ztkent/sunlight-meter/internal/db"
	"github.com/Ztkent/sunlight-meter/internal/sunlightmeter"
	slm "github.com/Ztkent/sunlight-meter/internal/sunlightmeter"
	"github.com/Ztkent/sunlight-meter/tsl2591"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

func main() {
	// Log the process ID, in case we need it.
	pid := os.Getpid()
	log.Println("SunlightMeter PID: ", pid)

	// connect to the lux sensor
	device, err := tsl2591.NewTSL2591(
		tsl2591.TSL2591_GAIN_LOW,
		tsl2591.TSL2591_INTEGRATIONTIME_300MS,
		"/dev/i2c-1",
	)
	if err != nil {
		log.Println(fmt.Sprintf("Failed to connect to the TSL2591 sensor: %v", err))
	}

	// connect to the sqlite database
	slmDB, err := db.ConnectSqlite(slm.DB_PATH)
	if err != nil {
		log.Fatalf("Failed to connect to the sqlite database: %v", err)
	}

	// Initialize router
	r := chi.NewRouter()
	// Log requests and recover from panics
	r.Use(middleware.Logger)
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if err := recover(); err != nil {
					sunlightmeter.ServeResponse(w, (fmt.Sprintf("%v", err)))
				}
			}()
			next.ServeHTTP(w, r)
		})
	})

	// Define routes
	defineRoutes(r, &slm.SLMeter{
		TSL2591:        device,
		ResultsDB:      slmDB,
		LuxResultsChan: make(chan slm.LuxResults),
		Pid:            pid,
	})

	// Start server
	app_port := "80"
	if os.Getenv("APP_PORT") != "" {
		app_port = os.Getenv("APP_PORT")
	}

	log.Println("SunlightMeter is running on port " + app_port)
	log.Fatal(http.ListenAndServe(":"+app_port, r))
	return
}

func defineRoutes(r *chi.Mux, meter *slm.SLMeter) {
	// Listen for any messages from our jobs, record them in sqlite
	go meter.MonitorAndRecordResults()

	// Sunlight Meter Controls
	r.Get("/", meter.ServeDashboard())
	r.Get("/start", meter.Start())
	r.Get("/stop", meter.Stop())
	r.Get("/signal-strength", meter.SignalStrength())
	r.Get("/current-conditions", meter.CurrentConditions())
	r.Get("/export", meter.ServeResultsDB())
	r.Post("/api/graph", meter.ServeResultsGraph())
	r.Get("/api/controls", meter.ServeSunlightControls())
	r.Get("/api/status", meter.ServeStatus())
	r.Get("/api/results", meter.ServeResultsTab())
	r.Get("/api/clear", meter.Clear())

	// Serve static files
	workDir, _ := os.Getwd()
	filesDir := filepath.Join(workDir, "internal", "sunlightmeter")
	FileServer(r, "/", http.Dir(filesDir))
}

func FileServer(r chi.Router, path string, root http.FileSystem) {
	r.Get(path+"*", func(w http.ResponseWriter, r *http.Request) {
		http.StripPrefix(path, http.FileServer(root)).ServeHTTP(w, r)
	})
}
