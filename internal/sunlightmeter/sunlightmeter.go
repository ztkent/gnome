package sunlightmeter

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"html/template"
	"log"
	"math"
	"net/http"
	"os/exec"
	"strconv"
	"strings"
	"time"

	"github.com/Ztkent/sunlight-meter/internal/tools"
	"github.com/Ztkent/sunlight-meter/tsl2591"
	"github.com/go-echarts/go-echarts/v2/charts"
	"github.com/go-echarts/go-echarts/v2/components"
	"github.com/go-echarts/go-echarts/v2/opts"
	"github.com/go-echarts/go-echarts/v2/types"
	"github.com/google/uuid"
)

type SLMeter struct {
	*tsl2591.TSL2591
	LuxResultsChan chan LuxResults
	ResultsDB      *sql.DB
	cancel         context.CancelFunc
	Pid            int
}

type LuxResults struct {
	Lux          float64
	Infrared     float64
	Visible      float64
	FullSpectrum float64
	JobID        string
}

type Conditions struct {
	JobID                 string  `json:"jobID"`
	Lux                   float64 `json:"lux"`
	FullSpectrum          float64 `json:"fullSpectrum"`
	Visible               float64 `json:"visible"`
	Infrared              float64 `json:"infrared"`
	DateRange             string  `json:"dateRange"`
	RecordedHoursInRange  float64 `json:"recordedHoursInRange"`
	FullSunlightInRange   float64 `json:"fullSunlightInRange"`
	LightConditionInRange string  `json:"lightConditionInRange"`
	AverageLuxInRange     float64 `json:"averageLuxInRange"`
}

const (
	MAX_JOB_DURATION = 8 * time.Hour
	RECORD_INTERVAL  = 30 * time.Second
	DB_PATH          = "sunlightmeter.db"
)

// Start the sensor, and collect data in a loop
func (m *SLMeter) Start() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		log.Println("It's gonna be a bright, bright sun-shining day!")
		if m.TSL2591 == nil {
			ServeResponse(w, r, "The sensor is not connected", http.StatusBadRequest)
			return
		} else if m.Enabled {
			ServeResponse(w, r, "The sensor is already started", http.StatusBadRequest)
			return
		}

		go func() {
			// Create a new context with a timeout to manage the sensor lifecycle
			ctx, cancel := context.WithTimeout(context.Background(), MAX_JOB_DURATION)
			m.cancel = cancel

			// Enable the sensor
			m.Enable()
			defer m.Disable()

			jobID := uuid.New().String()
			ticker := time.NewTicker(RECORD_INTERVAL)
			for {
				// Check if we've cancelled this job.
				select {
				case <-ctx.Done():
					log.Println("Job Cancelled, stopping sensor")
					return
				default:
				}

				// Read the sensor
				ch0, ch1, err := m.GetFullLuminosity()
				if err != nil {
					log.Println(fmt.Sprintf("The sensor failed to get luminosity: %s", err.Error()))
					m.LuxResultsChan <- LuxResults{
						JobID: jobID,
					}
					<-ticker.C
					continue
				}
				tools.DebugLog(fmt.Sprintf("0x%04x 0x%04x\n", ch0, ch1))

				// Calculate the lux value from the sensor readings
				lux, err := m.CalculateLux(ch0, ch1)
				if err != nil {
					log.Println(fmt.Sprintf("The sensor failed to calculate lux: %s", err.Error()))
					log.Println("Attempting to set new optimal sensor gain")
					err = m.SetOptimalGain()
					if err != nil {
						log.Println(fmt.Sprintf("The sensor failed to determine new optimal gain: %s", err.Error()))
					} else {
						log.Println("The sensor has been reconfigured with a new optimal gain")
					}
					time.Sleep(5 * time.Second)
					continue
				}

				// Send the results to the LuxResultsChan
				m.LuxResultsChan <- LuxResults{
					Lux:          lux,
					Visible:      tsl2591.GetNormalizedOutput(tsl2591.TSL2591_VISIBLE, ch0, ch1),
					Infrared:     tsl2591.GetNormalizedOutput(tsl2591.TSL2591_INFRARED, ch0, ch1),
					FullSpectrum: tsl2591.GetNormalizedOutput(tsl2591.TSL2591_FULLSPECTRUM, ch0, ch1),
					JobID:        jobID,
				}
				<-ticker.C
			}
		}()
		w.WriteHeader(http.StatusOK)
		ServeResponse(w, r, "Sunlight Reading Started", http.StatusOK)
		return
	}
}

// Stop the sensor, and cancel the job context
func (m *SLMeter) Stop() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if m.TSL2591 == nil {
			ServeResponse(w, r, "The sensor is not connected", http.StatusBadRequest)
			return
		} else if !m.Enabled {
			ServeResponse(w, r, "The sensor is already stopped", http.StatusBadRequest)
			return
		}

		// Stop the sensor, cancel the job context
		defer m.Disable()
		m.cancel()

		w.WriteHeader(http.StatusOK)
		ServeResponse(w, r, "Sunlight Reading Stopped", http.StatusOK)
		return
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
		conditions, err := m.getCurrentConditions()
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
		return
	}
}

// Return the most recent entry saved to the db
func (m *SLMeter) getCurrentConditions() (Conditions, error) {
	if m.TSL2591 == nil || !m.Enabled {
		return Conditions{}, nil
	}
	conditions := Conditions{}
	row := m.ResultsDB.QueryRow("SELECT job_id, lux, full_spectrum, visible, infrared FROM sunlight ORDER BY id DESC LIMIT 1")
	err := row.Scan(&conditions.JobID, &conditions.Lux, &conditions.FullSpectrum, &conditions.Visible, &conditions.Infrared)
	if err != nil {
		log.Println(err)
		return Conditions{}, err
	}
	return conditions, nil
}

// Return the most recent entry saved to the db
func (m *SLMeter) getHistoricalConditions(conditions Conditions, startDate string, endDate string) (Conditions, error) {
	if m.ResultsDB == nil {
		return conditions, nil
	}
	// Set the date range
	conditions.DateRange = fmt.Sprintf("%s - %s UTC", startDate, endDate)

	// Get the average lux for the date range
	row := m.ResultsDB.QueryRow(`
    SELECT 
        COALESCE(AVG(lux), 0), 
        COALESCE(MIN(created_at), '0001-01-01 00:00:00'), 
        COALESCE(MAX(created_at), '0001-01-01 00:00:00') 
    FROM sunlight 
    WHERE created_at BETWEEN ? AND ?`, startDate, endDate)
	var oldest, mostRecent sql.NullString
	err := row.Scan(&conditions.AverageLuxInRange, &oldest, &mostRecent)
	if err != nil {
		return conditions, err
	}
	if conditions.AverageLuxInRange == 0 {
		conditions.LightConditionInRange = "No Data in Range"
		return conditions, nil
	}

	// Get the number of hours where the average lux was above 10k
	rows, err := m.ResultsDB.Query(`
    SELECT COUNT(*) 
    FROM (
        SELECT AVG(lux) as avg_lux 
        FROM sunlight 
        WHERE created_at BETWEEN ? AND ? 
        GROUP BY strftime('%H:%M', created_at)
    ) 
    WHERE avg_lux > 10000`, startDate, endDate)
	if err != nil {
		return conditions, err
	}

	defer rows.Close()
	var fullSunlightInRangeMin sql.NullFloat64
	if rows.Next() {
		err = rows.Scan(&fullSunlightInRangeMin)
		if err != nil {
			return conditions, err
		}
	}
	if fullSunlightInRangeMin.Valid {
		conditions.FullSunlightInRange = fullSunlightInRangeMin.Float64 / 60
	}

	// Determine the light condition for the date range
	if oldest.Valid && mostRecent.Valid {
		mostRecent, oldest, err := tools.StartAndEndDateToTime(oldest.String, mostRecent.String)
		if err != nil {
			return conditions, err
		}
		conditions.RecordedHoursInRange = oldest.Sub(mostRecent).Hours()
		if conditions.FullSunlightInRange/conditions.RecordedHoursInRange > 0.5 {
			conditions.LightConditionInRange = "Full Sun"
		} else if conditions.FullSunlightInRange/conditions.RecordedHoursInRange > 0.25 {
			conditions.LightConditionInRange = "Partial Sun"
		} else if conditions.FullSunlightInRange/conditions.RecordedHoursInRange > 0.1 {
			conditions.LightConditionInRange = "Partial Shade"
		} else {
			conditions.LightConditionInRange = "Shade"
		}
	}

	return conditions, nil
}

// Check the signal strength of the wifi connection
func (m *SLMeter) SignalStrength() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		cmd := exec.Command("sh", "-c", "iw dev wlan0 link | grep 'signal:' | awk '{print $2}'")
		output, err := cmd.Output()
		if err != nil {
			log.Println(err)
			ServeResponse(w, r, err.Error(), http.StatusInternalServerError)
			return
		}
		signalInt, err := strconv.Atoi(strings.TrimSpace(string(output)))
		if err != nil {
			ServeResponse(w, r, "Device is not connected to a network", http.StatusBadRequest)
			return
		}

		// Convert the signal to a strength value
		// https://git.openwrt.org/?p=project/iwinfo.git;a=blob;f=iwinfo_nl80211.c;hb=HEAD#l2885
		if signalInt < -110 {
			signalInt = -110
		} else if signalInt > -40 {
			signalInt = -40
		}

		// Scale the signal to a percentage
		strength := (signalInt + 110) * 100 / 70
		if strength < 0 {
			strength = 0
		} else if strength > 100 {
			strength = 100
		}

		log.Println("Signal: ", fmt.Sprintf("%d", signalInt), " dBm")
		log.Println("Strength: ", strength, "%")

		w.WriteHeader(http.StatusOK)
		ServeResponse(w, r, "Signal Strength: "+fmt.Sprintf("%d", signalInt)+" dBm\nQuality: "+fmt.Sprintf("%d", strength)+"%", http.StatusOK)
		return
	}
}

// Serve the sqlite db for download
func (m *SLMeter) ServeResultsDB() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%s", "sunlightmeter.db"))
		w.Header().Set("Content-Type", "application/octet-stream")
		http.ServeFile(w, r, DB_PATH)
	}
}

// Serve the homepage
func (m *SLMeter) ServeDashboard() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html")
		w.WriteHeader(http.StatusOK)
		http.ServeFile(w, r, "internal/html/dashboard.html")
	}
}

// Serve the controls for the sensor, start/stop/export/current-conditions/signal-strength
func (m *SLMeter) ServeSunlightControls() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tmpl, err := template.ParseFiles("internal/html/templates/controls.gohtml")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		err = tmpl.Execute(w, nil)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

// Status of the sensor
func (m *SLMeter) ServeSensorStatus() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		tmpl, err := template.ParseFiles("internal/html/templates/status.gohtml")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		// Setup the status response
		type Status struct {
			Connected bool
			Enabled   bool
		}
		status := Status{}
		if m.TSL2591 == nil {
			status.Connected = false
		} else {
			status.Connected = true
			status.Enabled = m.Enabled
		}

		err = tmpl.Execute(w, status)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

// Serve the results graph
func (m *SLMeter) ServeResultsGraph() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// Get the date range for the graph from the request
		startDate, endDate := tools.ParseStartAndEndDate(r)

		// Query the database for the lux and created_at values
		rows, err := m.ResultsDB.Query("SELECT lux, created_at FROM sunlight WHERE created_at BETWEEN ? AND ? ORDER BY created_at", startDate, endDate)
		if err != nil {
			log.Println(err)
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		defer rows.Close()

		// Prepare the data for the chart
		var luxValues []opts.LineData
		var timeValues []string
		var maxLux int
		for rows.Next() {
			var lux string
			var createdAt time.Time
			if err := rows.Scan(&lux, &createdAt); err != nil {
				log.Println(err)
				http.Error(w, err.Error(), http.StatusInternalServerError)
				return
			}

			// Convert lux to float64
			luxFloat, err := strconv.ParseFloat(lux, 64)
			if err != nil {
				log.Println(err)
				http.Error(w, err.Error(), http.StatusInternalServerError)
				return
			}

			// Format the timestamp
			timeString := createdAt.Format("2006-01-02 15:04:05")
			if luxFloat > float64(maxLux) {
				// Round up to the nearest 5000
				maxLux = int(math.Ceil(luxFloat/5000) * 5000)
			}
			luxValues = append(luxValues, opts.LineData{Value: luxFloat})
			timeValues = append(timeValues, timeString)
		}

		// Create a new line chart
		line := charts.NewLine()

		// Add series for each level
		levels := map[int]string{
			500:   "DarkGrey",
			1000:  "WhiteSmoke",
			10000: "SkyBlue",
			25000: "Yellow",
		}
		titles := map[int]string{
			500:   "Shade",
			1000:  "Partial Shade",
			10000: "Partial Sun",
			25000: "Full Sun",
		}

		for level, color := range levels {
			line.AddSeries(
				fmt.Sprintf("%s", titles[level]),
				func(level int, length int) []opts.LineData {
					data := make([]opts.LineData, length)
					for i := range data {
						data[i] = opts.LineData{Value: level}
					}
					return data
				}(level, len(timeValues)),
				charts.WithLineChartOpts(opts.LineChart{
					Color: color,
				}),
			)
		}

		line.SetGlobalOptions(
			charts.WithInitializationOpts(opts.Initialization{
				Theme: types.ThemeChalk,
			}),
			charts.WithTitleOpts(opts.Title{
				// Title: "Lux over time",
			}),
			charts.WithXAxisOpts(opts.XAxis{
				Name: "Time",
			}),
			charts.WithYAxisOpts(opts.YAxis{
				Name: "Lux",
				Min:  "0",
				Max:  fmt.Sprintf("%d", maxLux),
			}),
			charts.WithTooltipOpts(opts.Tooltip{
				// Enable hover with a custom tooltip display
				Show:      true,
				Trigger:   "axis",
				TriggerOn: "mousemove",
				Formatter: "{a4}: {c4}<br> Time: {b0}",
			}),
			charts.WithToolboxOpts(opts.Toolbox{
				Show: true,
				Feature: &opts.ToolBoxFeature{
					SaveAsImage: &opts.ToolBoxFeatureSaveAsImage{
						Show:  true,
						Title: "Save as Image",
						Name:  "sunlight-meter",
					},
				},
			}),
		)
		line.SetXAxis(timeValues).AddSeries("Lux", luxValues)

		// Create a new page and add the line chart to it
		page := components.NewPage()
		page.AddCharts(line)

		// Render the graphs
		w.Header().Set("Content-Type", "text/html")
		page.Render(w)
		// Trigger an update for the results tab
		w.Write([]byte(`<div id='resultUpdateTrigger' hx-post='/sunlightmeter/results' hx-target='#resultsContent' hx-trigger='load'></div>`))
		w.Write([]byte(`<script>document.title = "Sunlight Meter";</script>`))
	}
}

// Update the info in the results tab
func (m *SLMeter) ServeResultsTab() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		conditions, err := m.getCurrentConditions()
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		startDate, endDate := tools.ParseStartAndEndDate(r)
		conditions, err = m.getHistoricalConditions(conditions, startDate, endDate)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		tmpl, err := template.ParseFiles("internal/html/templates/results.gohtml")
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		type ConditionsForDisplay struct {
			JobID                 string `json:"jobID"`
			Lux                   string `json:"lux"`
			FullSpectrum          string `json:"fullSpectrum"`
			Visible               string `json:"visible"`
			Infrared              string `json:"infrared"`
			DateRange             string `json:"dateRange"`
			RecordedHoursInRange  string `json:"recordedHoursInRange"`
			FullSunlightInRange   string `json:"fullSunlightInRange"`
			LightConditionInRange string `json:"lightConditionInRange"`
			AverageLuxInRange     string `json:"averageLuxInRange"`
			StartDate             string `json:"startDate"`
			EndDate               string `json:"endDate"`
		}
		err = tmpl.Execute(w, ConditionsForDisplay{
			JobID:                 conditions.JobID,
			Lux:                   fmt.Sprintf("%.4f", conditions.Lux),
			FullSpectrum:          fmt.Sprintf("%.4f", conditions.FullSpectrum),
			Visible:               fmt.Sprintf("%.4f", conditions.Visible),
			Infrared:              fmt.Sprintf("%.4f", conditions.Infrared),
			DateRange:             conditions.DateRange,
			RecordedHoursInRange:  fmt.Sprintf("%.4f", conditions.RecordedHoursInRange),
			FullSunlightInRange:   fmt.Sprintf("%.4f", conditions.FullSunlightInRange),
			LightConditionInRange: conditions.LightConditionInRange,
			AverageLuxInRange:     fmt.Sprintf("%.4f", conditions.AverageLuxInRange),
			StartDate:             startDate,
			EndDate:               endDate,
		})
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}
}

// Used to clear a div with htmx
func (m *SLMeter) Clear() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
	}
}

// Populate the response div with a message, or reply with a JSON message
func ServeResponse(w http.ResponseWriter, r *http.Request, message string, status int) {
	if strings.Contains(r.URL.Path, "/api/v1/") {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(status)
		json.NewEncoder(w).Encode(map[string]string{"message": message})
		return
	}

	tmpl, err := template.ParseFiles("internal/html/templates/response.gohtml")
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	err = tmpl.Execute(w, message)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
}

// Read from LuxResultsChan, write the results to sqlite
func (m *SLMeter) MonitorAndRecordResults() {
	log.Println("Monitoring for new Sunlight Messages...")
	for {
		select {
		case result := <-m.LuxResultsChan:
			log.Println(fmt.Sprintf("- JobID: %s, Lux: %.5f", result.JobID, result.Lux))
			if math.IsInf(result.Lux, 1) {
				log.Println("Lux is infinite, skipping record")
				continue
			}
			_, err := m.ResultsDB.Exec(
				"INSERT INTO sunlight (job_id, lux, full_spectrum, visible, infrared) VALUES (?, ?, ?, ?, ?)",
				result.JobID,
				fmt.Sprintf("%.5f", result.Lux),
				fmt.Sprintf("%.5e", result.FullSpectrum),
				fmt.Sprintf("%.5e", result.Visible),
				fmt.Sprintf("%.5e", result.Infrared),
			)
			if err != nil {
				log.Println(err)
			}
		}
	}
}
