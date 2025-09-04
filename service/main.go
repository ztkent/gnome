package main

import (
	"database/sql"
	"fmt"
	"io/fs"
	"log"
	"net/http"
	"os"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/ztkent/gnome/internal/gnome"
	"github.com/ztkent/gnome/internal/gnome/tsl2591"
	"github.com/ztkent/gnome/internal/tools"
)

func main() {
	// Log the process ID, in case we need it.
	pid := os.Getpid()
	log.Println("Gnome PID: ", pid)

	// connect to the sqlite database
	gnomeDB, err := tools.ConnectSqlite(gnome.GNOME_DB_PATH)
	if err != nil {
		log.Fatalf("Failed to connect to the sqlite database: %v", err)
	}

	// Connect and start the Sunlight Meter
	startSunLightMeter(gnomeDB, pid)
}

func startSunLightMeter(gnomeDB *sql.DB, pid int) {
	// Connect the TSL2591 sensor
	device, err := tsl2591.NewTSL2591(
		tsl2591.TSL2591_GAIN_LOW,
		tsl2591.TSL2591_INTEGRATIONTIME_300MS,
		"/dev/i2c-1",
	)
	if err != nil {
		log.Printf("Failed to connect to the TSL2591 sensor: %v", err)
	}

	slMeter := gnome.SLMeter{
		TSL2591:        device,
		ResultsDB:      gnomeDB,
		LuxResultsChan: make(chan gnome.LuxResults),
		Pid:            pid,
	}

	// Start a new chi router
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(handleServerPanic)
	defineRoutes(r, &slMeter)

	// Lets start the sensor off the jump, if we can.
	go slMeter.StartSensor()

	// Default to an HTTP server
	app_port := "8080"
	log.Printf("Starting HTTP server on port %s", app_port)
	err = http.ListenAndServe(":"+app_port, r)
	if err != nil {
		log.Fatalf("Failed to start HTTP server: %v", err)
	}
}

func defineRoutes(r *chi.Mux, meter *gnome.SLMeter) {
	// Listen for any result messages from our jobs, record them in sqlite
	go meter.MonitorAndRecordResults()

	// Sunlight API, these serve a JSON response
	r.Get("/id", meter.ID())
	r.Route("/api/v1", func(r chi.Router) {
		r.Get("/start", meter.Start())
		r.Get("/stop", meter.Stop())
		r.Get("/signal-strength", meter.SignalStrength())
		r.Get("/current-conditions", meter.CurrentConditions())
		r.Get("/export", meter.ServeResultsDB())
		r.Get("/csv", meter.ServeResultsCSV())
		r.Get("/graph", meter.ServeResultsJSON())
	})

	// Dashboard routes
	r.Get("/", meter.Dashboard())
	r.Route("/dashboard", func(r chi.Router) {
		r.Get("/device-status", meter.DashboardDeviceStatus())
		r.Get("/current-conditions", meter.DashboardCurrentConditions())
		r.Get("/signal-strength", meter.DashboardSignalStrength())
		r.Get("/controls", meter.DashboardControls())
		r.Get("/system-info", meter.DashboardSystemInfo())
		r.Get("/historical-graph", meter.DashboardHistoricalGraph())
	})

	// Static files handler for JS, CSS and other assets
	r.Get("/html/*", func(w http.ResponseWriter, r *http.Request) {
		// Read the file from the embedded filesystem
		fileSystem, err := fs.Sub(meter.GetTemplateFiles(), ".")
		if err != nil {
			http.Error(w, "File system error", http.StatusInternalServerError)
			return
		}

		// Set the correct MIME type based on file extension
		filePath := strings.TrimPrefix(r.URL.Path, "/")
		if strings.HasSuffix(filePath, ".js") {
			w.Header().Set("Content-Type", "application/javascript")
		} else if strings.HasSuffix(filePath, ".css") {
			w.Header().Set("Content-Type", "text/css")
		}

		// Serve the file
		http.FileServer(http.FS(fileSystem)).ServeHTTP(w, r)
	})
}

func handleServerPanic(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if err := recover(); err != nil {
				gnome.ServeResponse(w, r, (fmt.Sprintf("%v", err)), http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, r)
	})
}
