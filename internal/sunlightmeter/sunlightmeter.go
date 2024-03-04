package sunlightmeter

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os/exec"
	"strconv"
	"strings"
	"time"

	"github.com/Ztkent/sunlight-meter/internal/tools"
	"github.com/Ztkent/sunlight-meter/tsl2591"
	"github.com/google/uuid"
)

type SLMeter struct {
	*tsl2591.TSL2591
	LuxResultsChan chan LuxResults
	ResultsDB      *sql.DB
	cancel         context.CancelFunc
}

type LuxResults struct {
	Lux          float64
	Infrared     float64
	Visible      float64
	FullSpectrum float64
	JobID        string
}

const (
	MAX_JOB_DURATION = 8 * time.Hour
	RECORD_INTERVAL  = 30 * time.Second
	DB_PATH          = "results/sunlightmeter.db"
)

// Start the sensor, and collect data in a loop
func (m *SLMeter) Start() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		log.Println("It's gonna be a bright, bright sun-shining day!")
		if m.Enabled {
			http.Error(w, "The sensor is already running", http.StatusConflict)
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
		w.Write([]byte("Sunlight Reading Started"))
	}
}

func (m *SLMeter) Stop() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !m.Enabled {
			http.Error(w, "The sensor is already stopped", http.StatusConflict)
			return
		}

		// Stop the sensor, cancel the job context
		defer m.Disable()
		m.cancel()

		w.WriteHeader(http.StatusOK)
		w.Write([]byte("Sunlight Reading Stopped"))
	}
}

func (m *SLMeter) SignalStrength() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		cmd := exec.Command("sh", "-c", "iw dev wlan0 link | grep 'signal:' | awk '{print $2}'")
		output, err := cmd.Output()
		if err != nil {
			log.Println(err)
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		signalInt, err := strconv.Atoi(strings.TrimSpace(string(output)))
		if err != nil {
			log.Println(err)
			http.Error(w, err.Error(), http.StatusInternalServerError)
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
		w.Write([]byte("Signal Strength: " + fmt.Sprintf("%d", signalInt) + " dBm\nQuality: " + fmt.Sprintf("%d", strength) + "%"))
	}
}

func (m *SLMeter) ServeResultsDB() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%s", "sunlight.db"))
		w.Header().Set("Content-Type", "application/octet-stream")
		http.ServeFile(w, r, DB_PATH)
	}
}

// TODO: https://github.com/go-echarts/go-echarts
func (m *SLMeter) ServeResultsDashboard() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(""))
	}
}

// Read from LuxResultsChan, write the results to sqlite
func (m *SLMeter) MonitorAndRecordResults() {
	log.Println("Monitoring for new Sunlight Messages...")
	for {
		select {
		case result := <-m.LuxResultsChan:
			log.Println(fmt.Sprintf("- JobID: %s, Lux: %.5f", result.JobID, result.Lux))
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

// Return the most recent entry saved to the db
func (m *SLMeter) CurrentConditions() http.HandlerFunc {
	type Conditions struct {
		JobID        string  `json:"jobID"`
		Lux          float64 `json:"lux"`
		FullSpectrum float64 `json:"fullSpectrum"`
		Visible      float64 `json:"visible"`
		Infrared     float64 `json:"infrared"`
	}
	return func(w http.ResponseWriter, r *http.Request) {
		if !m.Enabled {
			http.Error(w, "The sensor is not running", http.StatusConflict)
			return
		}
		data := Conditions{}
		row := m.ResultsDB.QueryRow("SELECT job_id, lux, full_spectrum, visible, infrared FROM sunlight ORDER BY id DESC LIMIT 1")
		err := row.Scan(&data.JobID, &data.Lux, &data.FullSpectrum, &data.Visible, &data.Infrared)
		if err != nil {
			log.Println(err)
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		jsonData, err := json.Marshal(data)
		if err != nil {
			log.Println(err)
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}

		w.WriteHeader(http.StatusOK)
		w.Write(jsonData)
	}
}
