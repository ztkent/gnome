package gnome

import (
	"embed"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
	"time"

	"github.com/ztkent/gnome/internal/tools"
)

//go:embed html/*
var templateFiles embed.FS

func (m *SLMeter) Start() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if err := m.StartSensor(); err != nil {
			ServeResponse(w, r, err.Error(), http.StatusBadRequest)
			return
		}
		ServeResponse(w, r, "Sunlight Reading Started", http.StatusOK)
	}
}

func (m *SLMeter) Stop() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if err := m.StopSensor(); err != nil {
			ServeResponse(w, r, err.Error(), http.StatusBadRequest)
			return
		}
		ServeResponse(w, r, "Sunlight Reading Stopped", http.StatusOK)
	}
}

// Serve data about the most recent entry saved to the db
func (m *SLMeter) CurrentConditions() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if m.TSL2591 == nil {
			ServeResponse(w, r, "The sensor is not connected", http.StatusBadRequest)
			return
		} else if !m.Enabled {
			ServeResponse(w, r, "The sensor is not enabled", http.StatusBadRequest)
			return
		}

		conditions, err := m.GetCurrentConditions()
		if err != nil {
			log.Println(err)
			ServeResponse(w, r, err.Error(), http.StatusInternalServerError)
			return
		}

		conditionsData, err := json.Marshal(conditions)
		if err != nil {
			log.Println(err)
			ServeResponse(w, r, err.Error(), http.StatusInternalServerError)
			return
		}

		w.WriteHeader(http.StatusOK)
		ServeResponse(w, r, string(conditionsData), http.StatusOK)
	}
}

// Check the signal strength of the wifi connection
func (m *SLMeter) SignalStrength() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		signal, err := m.GetSignalStrength()
		if err != nil {
			log.Println(err)
			ServeResponse(w, r, err.Error(), http.StatusInternalServerError)
			return
		}
		log.Printf("Signal: %d dBm\nStrength: %d%%\n", signal.SignalInt, signal.Strength)
		ServeResponse(w, r, fmt.Sprintf("Signal Strength: %d dBm\nQuality: %d%%", signal.SignalInt, signal.Strength), http.StatusOK)
	}
}

type ServiceResponse struct {
	ServiceName    string            `json:"service_name"`
	OutboundIP     string            `json:"outbound_ip"`
	MACAddresses   []string          `json:"mac_addresses"`
	SignalStrength SignalStrength    `json:"signal_strength"`
	Conditions     Conditions        `json:"conditions"`
	Status         Status            `json:"status"`
	Errors         map[string]string `json:"errors,omitempty"`
}

func (m *SLMeter) ID() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		response := ServiceResponse{
			ServiceName: "Gnome",
			OutboundIP:  tools.GetOutboundIP().String(),
			Errors:      make(map[string]string),
		}

		macs, err := tools.GetAllActiveMACAddresses()
		if err != nil {
			response.Errors["mac_addresses"] = err.Error()
			response.MACAddresses = []string{}
		} else {
			response.MACAddresses = macs
		}

		signalStrength, err := m.GetSignalStrength()
		if err != nil {
			response.Errors["signal_strength"] = err.Error()
			response.SignalStrength = SignalStrength{SignalInt: 0, Strength: 0}
		} else {
			response.SignalStrength = signalStrength
		}

		conditions, err := m.GetCurrentConditions()
		if err != nil {
			response.Errors["conditions"] = err.Error()
			response.Conditions = Conditions{}
		} else {
			response.Conditions = Conditions{
				JobID:                 conditions.JobID,
				Lux:                   sanitizeFloat64(conditions.Lux),
				FullSpectrum:          sanitizeFloat64(conditions.FullSpectrum),
				Visible:               sanitizeFloat64(conditions.Visible),
				Infrared:              sanitizeFloat64(conditions.Infrared),
				DateRange:             conditions.DateRange,
				RecordedHoursInRange:  sanitizeFloat64(conditions.RecordedHoursInRange),
				FullSunlightInRange:   sanitizeFloat64(conditions.FullSunlightInRange),
				LightConditionInRange: conditions.LightConditionInRange,
				AverageLuxInRange:     sanitizeFloat64(conditions.AverageLuxInRange),
			}
		}

		status, err := m.GetSensorStatus()
		if err != nil {
			response.Errors["status"] = err.Error()
			response.Status = Status{Connected: false, Enabled: false}
		} else {
			response.Status = status
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		if err := json.NewEncoder(w).Encode(response); err != nil {
			log.Printf("Error encoding response: %v", err)
		}
	}
}

func sanitizeFloat64(value float64) float64 {
	if math.IsNaN(value) || math.IsInf(value, 0) {
		return 0
	}
	return value
}

// Serve the sqlite db for download
func (m *SLMeter) ServeResultsDB() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%s", "gnome.db"))
		w.Header().Set("Content-Type", "application/octet-stream")
		http.ServeFile(w, r, GNOME_DB_PATH)
	}
}

func (m *SLMeter) ServeResultsCSV() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		err := tools.ExportToCSV(GNOME_DB_PATH, GNOME_CSV_PATH)
		if err != nil {
			http.Error(w, "Failed to export CSV", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%s", "gnome.csv"))
		w.Header().Set("Content-Type", "text/csv")
		http.ServeFile(w, r, GNOME_CSV_PATH)
	}
}

func (m *SLMeter) ServeResultsJSON() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		r.ParseForm()
		startDate := time.Now().UTC().Add(-8 * time.Hour)
		endDate := time.Now().UTC()

		startDateStr := r.FormValue("start")
		endDateStr := r.FormValue("end")
		if startDateStr != "" && endDateStr != "" {
			var err error
			startDate, err = time.Parse(time.RFC3339, startDateStr)
			if err != nil {
				http.Error(w, "Invalid start date", http.StatusBadRequest)
				return
			}
			endDate, err = time.Parse(time.RFC3339, endDateStr)
			if err != nil {
				http.Error(w, "Invalid end date", http.StatusBadRequest)
				return
			}
		}

		data, err := tools.ExportToJSON(GNOME_DB_PATH, startDate, endDate)
		if err != nil {
			http.Error(w, "Failed to export CSV", http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(data)
	}
}

// Populate the response div with a message, or reply with a JSON message
func ServeResponse(w http.ResponseWriter, r *http.Request, message string, status int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]string{"message": message})
}
