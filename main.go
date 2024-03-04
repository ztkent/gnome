package main

import (
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/Ztkent/sunlight-meter/internal/db"
	slm "github.com/Ztkent/sunlight-meter/internal/sunlightmeter"
	"github.com/Ztkent/sunlight-meter/tsl2591"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
)

func main() {
	// connect to the lux sensor
	device, err := tsl2591.NewTSL2591(
		tsl2591.TSL2591_GAIN_LOW,
		tsl2591.TSL2591_INTEGRATIONTIME_600MS,
		"/dev/i2c-1",
	)
	if err != nil {
		log.Fatalf("Failed to connect to the TSL2591 sensor: %v", err)
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
	r.Use(middleware.Recoverer)

	// Define routes
	defineRoutes(r, &slm.SLMeter{
		TSL2591:        device,
		ResultsDB:      slmDB,
		LuxResultsChan: make(chan slm.LuxResults),
	})

	// Start server
	app_port := "8080"
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
	r.Get("/", meter.Start())
	r.Get("/stop", meter.Stop())
	r.Get("/ss", meter.SignalStrength())
	r.Get("/export", meter.ServeResults())

	// Serve static files
	workDir, _ := os.Getwd()
	filesDir := filepath.Join(workDir, "internal", "html", "img")
	FileServer(r, "/img", http.Dir(filesDir))
	FileServer(r, "/favicon.ico", http.Dir(filesDir))
}

func FileServer(r chi.Router, path string, root http.FileSystem) {
	if strings.ContainsAny(path, "{}*") {
		panic("FileServer does not permit any URL parameters.")
	}

	if path != "/" && path[len(path)-1] != '/' {
		r.Get(path, http.RedirectHandler(path+"/", 301).ServeHTTP)
		path += "/"
	}
	r.Get(path+"*", func(w http.ResponseWriter, r *http.Request) {
		http.StripPrefix(path, http.FileServer(root)).ServeHTTP(w, r)
	})
}
