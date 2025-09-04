package gnome

import (
	"log"
	"net/http"
	"text/template"

	"github.com/ztkent/gnome/internal/tools"
)

// Dashboard route handlers
func (m *SLMeter) Dashboard() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tmpl, err := parseTemplateFile("html/index.html")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "text/html")
		err = tmpl.Execute(w, nil)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

func (m *SLMeter) DashboardDeviceStatus() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		response := m.getServiceResponse()

		tmpl, err := parseTemplateFile("html/templates/device-status.gohtml")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "text/html")
		err = tmpl.Execute(w, response)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

func (m *SLMeter) DashboardCurrentConditions() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		conditions, err := m.GetCurrentConditions()
		if err != nil {
			w.Header().Set("Content-Type", "text/html")
			w.Write([]byte(`<div class="error">Failed to load current conditions</div>`))
			return
		}

		// Sanitize values for display
		sanitizedConditions := Conditions{
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

		tmpl, err := parseTemplateFile("html/templates/current-conditions.gohtml")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "text/html")
		err = tmpl.Execute(w, sanitizedConditions)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

func (m *SLMeter) DashboardSignalStrength() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		signal, err := m.GetSignalStrength()
		if err != nil {
			w.Header().Set("Content-Type", "text/html")
			w.Write([]byte(`<div class="error">Failed to load signal strength</div>`))
			return
		}

		tmpl, err := parseTemplateFile("html/templates/signal-strength.gohtml")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "text/html")
		err = tmpl.Execute(w, signal)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

type ControlsData struct {
	Enabled     bool
	LastMessage string
}

func (m *SLMeter) DashboardControls() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		status, err := m.GetSensorStatus()
		if err != nil {
			w.Header().Set("Content-Type", "text/html")
			w.Write([]byte(`<div class="error">Failed to load controls</div>`))
			return
		}

		controlsData := ControlsData{
			Enabled: status.Enabled,
		}

		tmpl, err := parseTemplateFile("html/templates/controls.gohtml")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "text/html")
		err = tmpl.Execute(w, controlsData)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

func (m *SLMeter) DashboardSystemInfo() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		response := m.getServiceResponse()

		tmpl, err := parseTemplateFile("html/templates/system-info.gohtml")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "text/html")
		err = tmpl.Execute(w, response)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

func (m *SLMeter) DashboardHistoricalGraph() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tmpl, err := parseTemplateFile("html/templates/historical-graph.gohtml")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.Header().Set("Content-Type", "text/html")
		err = tmpl.Execute(w, nil)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

// Helper function to get service response data
func (m *SLMeter) getServiceResponse() ServiceResponse {
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

	return response
}

func parseTemplateFile(path string) (*template.Template, error) {
	content, err := templateFiles.ReadFile(path)
	if err != nil {
		log.Fatalf("failed to read embedded template: %v", err)
	}

	tmpl, err := template.New("results").Parse(string(content))
	if err != nil {
		log.Fatalf("failed to parse template: %v", err)
	}
	return tmpl, nil
}
