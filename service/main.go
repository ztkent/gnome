package main

import (
	"database/sql"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/ztkent/gnome/internal/gnome"
	"github.com/ztkent/gnome/internal/gnome/bme280"
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

	// Connect the BME280 environmental sensor (try I2C first, then SPI)
	envDevice, err := bme280.NewBME280("/dev/i2c-1", bme280.BME280_I2C_ADDR_PRIMARY)
	if err != nil {
		log.Printf("Failed to connect to BME280 via I2C: %v", err)
		log.Printf("Trying BME280 via SPI...")
		envDevice, err = bme280.NewBME280SPI("/dev/spidev0.0", 1000000)
		if err != nil {
			log.Printf("Failed to connect to BME280 via SPI: %v", err)
			envDevice = nil
		}
	}

	slMeter := gnome.SLMeter{
		TSL2591:                  device,
		BME280:                   envDevice,
		ResultsDB:                gnomeDB,
		LuxResultsChan:           make(chan gnome.LuxResults),
		EnvironmentalResultsChan: make(chan gnome.EnvironmentalResults),
		Pid:                      pid,
	}

	// Start a new chi router
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(handleServerPanic)
	defineRoutes(r, &slMeter)

	// Lets start the sensor off the jump, if we can.
	go slMeter.StartSensor()
	
	// Start environmental sensor if available
	if envDevice != nil {
		go slMeter.StartEnvironmentalSensor()
	}

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
	
	// Monitor environmental results if BME280 is available
	if meter.BME280 != nil {
		go meter.MonitorAndRecordEnvironmentalResults()
	}

	// Sunlight API, these serve a JSON response
	r.Route("/api/v1", func(r chi.Router) {
		r.Get("/start", meter.Start())
		r.Get("/stop", meter.Stop())
		r.Get("/signal-strength", meter.SignalStrength())
		r.Get("/current-conditions", meter.CurrentConditions())
		r.Get("/export", meter.ServeResultsDB())
		r.Get("/csv", meter.ServeResultsCSV())
		r.Get("/graph", meter.ServeResultsJSON())
		
		// Environmental API endpoints
		r.Get("/environmental/start", meter.StartEnvironmental())
		r.Get("/environmental/stop", meter.StopEnvironmental())
		r.Get("/environmental/status", meter.EnvironmentalStatus())
		r.Get("/environmental/current-conditions", meter.CurrentEnvironmentalConditions())
		r.Get("/environmental/csv", meter.ServeEnvironmentalResultsCSV())
		r.Get("/environmental/graph", meter.ServeEnvironmentalResultsJSON())
	})

	// Route for service identification
	r.Get("/", meter.ID())
	r.Get("/id", meter.ID())

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
				gnome.ServeResponse(w, r, (fmt.Sprintf("%v", err)), http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, r)
	})
}
