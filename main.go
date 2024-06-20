package main

import (
	"fmt"
	"log"
	"time"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"

	"github.com/Ztkent/sunlight-meter/internal/sunlightmeter"
	slm "github.com/Ztkent/sunlight-meter/internal/sunlightmeter"
	"github.com/Ztkent/sunlight-meter/internal/tools"
	"github.com/Ztkent/sunlight-meter/tsl2591"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"

	"github.com/muka/go-bluetooth/bluez/profile/adapter"
)

func enableBluetooth() error {
	// TODO: Maybe https://github.com/tinygo-org/bluetooth is better...

	log.Println("Configuration: Starting Bluetooth...")
	// Only support Linux, this should be running on a Raspberry Pi
	if runtime.GOOS != "linux" {
		return fmt.Errorf("Unsupported OS: %v", runtime.GOOS)
	} else {
		_, err := os.Stat("/proc/device-tree/model")
		if err != nil {
			return fmt.Errorf("Not a Raspberry Pi, can't enable Bluetooth Discovery: %v", err)
		}
	}

	// Get the default adapter
	defaultAdapter, err := adapter.GetDefaultAdapter()
	if err != nil {
		return fmt.Errorf("Failed to get default adapter: %v", err)
	}

	address, err := defaultAdapter.GetAddress()
	if err != nil {
		return fmt.Errorf("Failed to get device address: %v", err)
	}
	log.Printf("Device address: %s", address)

	err = defaultAdapter.SetAlias("SunlightMeter")
	if err != nil {
		return fmt.Errorf("Failed to set name: %v", err)
	}

	// Register an agent to handle pairing requests
	log.Println("Configuration: Registering Agent...")
	// err = defaultAdapter.RegisterAgent()

	// Make the device discoverable
	log.Println("Configuration: Setting Discoverable...")
	err = defaultAdapter.SetDiscoverable(true)
	if err != nil {
		return fmt.Errorf("Failed to make device discoverable: %v", err)
	}

	// Start the discovery
	log.Println("Configuration: Starting Discovery...")
	err = defaultAdapter.StartDiscovery()
	if err != nil {
		return fmt.Errorf("Failed to start bluetooth discovery: %v", err)
	}

	for {
		// Get discovered devices
		log.Println("Configuration: After Discovery - ")
		devices, err := defaultAdapter.GetDevices()
		if err != nil {
			return fmt.Errorf("Failed to get bluetooth devices: %v", err)
		}

		// Log them
		log.Println("Configuration: After GetDevices - ")
		for _, device := range devices {
			log.Printf("Discovered bluetooth device: %s : %v", device.Properties.Alias, device.Properties.Address)
			log.Printf("Properties: %v", device.Properties)
		}
		time.Sleep(15 * time.Second)
	}
	
	return nil
}

func manageConnection() error {
	// Ensure we have a active internet connection
	err := exec.Command("ping", "-c", "1", "ztkent.com").Run()
	if err != nil {
		log.Println("No internet connection, starting configuration...")

		//setup a Bluetooth server and wait for a client to connect.
		err = enableBluetooth()
		if err != nil {
			return fmt.Errorf("Failed to enable Bluetooth: %v", err)
		}

		err = exec.Command("ping", "-c", "1", "ztkent.com").Run()
		if err != nil {
			return fmt.Errorf("Failed to connect to the internet after configuration: %v", err)
		}
	}


	// On success, we can disable bluetooth and continue.
	return nil
}

func main() {
	// Log the process ID, in case we need it.
	pid := os.Getpid()
	log.Println("Sunlight Meter PID: ", pid)

	// Ensure we have a active internet connection
	err := manageConnection()
	if err != nil {
		log.Fatalf("Sunlight Meter failed to connect to the internet: %v", err)
	}

	// connect to the lux sensor
	device, err := tsl2591.NewTSL2591(
		tsl2591.TSL2591_GAIN_LOW,
		tsl2591.TSL2591_INTEGRATIONTIME_300MS,
		"/dev/i2c-1",
	)
	if err != nil {
		// Still want to start the server if we can't connect to the sensor.
		log.Println(fmt.Sprintf("Failed to connect to the TSL2591 sensor: %v", err))
	}

	// connect to the sqlite database
	slmDB, err := tools.ConnectSqlite(slm.DB_PATH)
	if err != nil {
		// Unlike connecting to the sensor, this should always work.
		log.Fatalf("Failed to connect to the sqlite database: %v", err)
	}

	// Initialize router
	r := chi.NewRouter()
	// Log requests and recover from panics
	r.Use(middleware.Logger)
	r.Use(handleServerPanic)

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

	log.Println("Sunlight Meter is running on port " + app_port)
	log.Fatal(http.ListenAndServe(":"+app_port, r))
	return
}

func defineRoutes(r *chi.Mux, meter *slm.SLMeter) {
	// Listen for any result messages from our jobs, record them in sqlite
	go meter.MonitorAndRecordResults()

	// Sunlight Meter Dashboard Controls
	r.Get("/", meter.ServeDashboard())
	r.Route("/sunlightmeter", func(r chi.Router) {
		r.Use(tools.CheckInNetwork)
		r.Get("/start", meter.Start())
		r.Get("/stop", meter.Stop())
		r.Get("/signal-strength", meter.SignalStrength())
		r.Get("/current-conditions", meter.CurrentConditions())
		r.Get("/export", meter.ServeResultsDB())
		r.Post("/graph", meter.ServeResultsGraph())
		r.Get("/controls", meter.ServeSunlightControls())
		r.Get("/status", meter.ServeSensorStatus())
		r.Post("/results", meter.ServeResultsTab())
		r.Get("/clear", meter.Clear())
	})

	// Sunlight Meter API, these serve a JSON response
	r.Route("/api/v1", func(r chi.Router) {
		r.Get("/start", meter.Start())
		r.Get("/stop", meter.Stop())
		r.Get("/signal-strength", meter.SignalStrength())
		r.Get("/current-conditions", meter.CurrentConditions())
		r.Get("/export", meter.ServeResultsDB())
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
