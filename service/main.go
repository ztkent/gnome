package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/ztkent/gnome/internal/gnome"
	"github.com/ztkent/gnome/internal/sunlightmeter"
	"github.com/ztkent/gnome/internal/sunlightmeter/tsl2591"
	"github.com/ztkent/gnome/internal/tools"
)

func main() {
	// Log the process ID, in case we need it.
	pid := os.Getpid()
	log.Println("Gnome PID: ", pid)

	// Manage wireless connection. Once we're past here, we should have internet.
	err := tools.ManageInternetConnection()
	if err != nil {
		log.Fatalf("Failed to manage internet connection: %v", err)
	}

	// connect to the sqlite database
	gnomeDB, err := tools.ConnectSqlite(gnome.GNOME_DB_PATH)
	if err != nil {
		log.Fatalf("Failed to connect to the sqlite database: %v", err)
	}

	// Connect and start the Sunlight Meter
	startSunLightMeter(gnomeDB, pid)
	return
}

func startSunLightMeter(gnomeDB *sql.DB, pid int) {
	// Connect the TSL2591 sensor
	device, err := tsl2591.NewTSL2591(
		tsl2591.TSL2591_GAIN_LOW,
		tsl2591.TSL2591_INTEGRATIONTIME_300MS,
		"/dev/i2c-1",
	)
	if err != nil {
		log.Fatalf("Failed to connect to the TSL2591 sensor: %v", err)
	}

	// Start a new chi router
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(handleServerPanic)
	defineRoutes(r, &sunlightmeter.SLMeter{
		TSL2591:        device,
		ResultsDB:      gnomeDB,
		LuxResultsChan: make(chan sunlightmeter.LuxResults),
		Pid:            pid,
	})

	if os.Getenv("SSL") == "true" {
		// Start the HTTPS server
		tools.EnsureCertificate("cert.pem", "key.pem")
		app_port := "443"
		certPath := "cert.pem"
		keyPath := "key.pem"
		log.Printf("Starting HTTPS server on port %s", app_port)
		err = http.ListenAndServeTLS(":"+app_port, certPath, keyPath, r)
		if err != nil {
			log.Fatalf("Failed to start HTTPS server: %v", err)
		}
	} else {
		// Default to an HTTP server
		app_port := "80"
		log.Printf("Starting HTTP server on port %s", app_port)
		err = http.ListenAndServe(":"+app_port, r)
		if err != nil {
			log.Fatalf("Failed to start HTTP server: %v", err)
		}
	}
}

func defineRoutes(r *chi.Mux, meter *sunlightmeter.SLMeter) {
	// Listen for any result messages from our jobs, record them in sqlite
	go meter.MonitorAndRecordResults()

	// Sunlight Dashboard Controls
	r.Get("/", meter.ServeDashboard())
	r.Route("/gnome", func(r chi.Router) {
		r.Get("/start", meter.Start())
		r.Get("/stop", meter.Stop())
		r.Get("/signal-strength", meter.SignalStrength())
		r.Get("/current-conditions", meter.CurrentConditions())
		r.Get("/export", meter.ServeResultsDB())
		r.Post("/graph", meter.ServeResultsGraph())
		r.Get("/controls", meter.ServeControls())
		r.Get("/status", meter.ServeSensorStatus())
		r.Post("/results", meter.ServeResultsTab())
		r.Get("/clear", meter.Clear())
	})

	// Sunlight API, these serve a JSON response
	r.Route("/api/v1", func(r chi.Router) {
		r.Get("/start", meter.Start())
		r.Get("/stop", meter.Stop())
		r.Get("/signal-strength", meter.SignalStrength())
		r.Get("/current-conditions", meter.CurrentConditions())
		r.Get("/export", meter.ServeResultsDB())
	})

	// Route for service identification
	r.Get("/id", func(w http.ResponseWriter, r *http.Request) {
		macs, err := tools.GetAllActiveMACAddresses()
		if err != nil {
			// Handle error, maybe log it or use a default/fallback value
			http.Error(w, "Failed to get MAC addresses", http.StatusInternalServerError)
			return
		}

		response := struct {
			ServiceName  string   `json:"service_name"`
			OutboundIP   string   `json:"outbound_ip"`
			MACAddresses []string `json:"mac_addresses"`
		}{
			ServiceName:  "Gnome",
			OutboundIP:   tools.GetOutboundIP().String(),
			MACAddresses: macs,
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(response)
	})

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

func handleServerPanic(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if err := recover(); err != nil {
				sunlightmeter.ServeResponse(w, r, (fmt.Sprintf("%v", err)), http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, r)
	})
}
