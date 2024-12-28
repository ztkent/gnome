package sunlightmeter

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"math"
	"os/exec"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/ztkent/gnome/internal/gnome"
	"github.com/ztkent/gnome/internal/sunlightmeter/tsl2591"
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

type Status struct {
	Connected bool `json:"connected"`
	Enabled   bool `json:"enabled"`
}

type SignalStrength struct {
	SignalInt int `json:"signalInt"`
	Strength  int `json:"strength"`
}

// Start the sensor, and collect data in a loop
func (m *SLMeter) StartSensor() error {
	if m.TSL2591 == nil {
		return fmt.Errorf("sensor is not connected")
	}
	if m.Enabled {
		return fmt.Errorf("sensor is already started")
	}

	// Create context with timeout
	// ctx, cancel := context.WithTimeout(context.Background(), gnome.MAX_JOB_DURATION)
	ctx, cancel := context.WithCancel(context.Background()) // Lets let it run forever
	m.cancel = cancel

	// Enable sensor
	m.Enable()

	// Initializing Sensor Optimal Gain
	log.Printf("Setting sensor initial gain & integration time")
	if err := m.SetOptimalGain(); err != nil {
		log.Printf("Failed to set initial optimal gain: %s, using default settings", err)
	}
	log.Printf("Current Sensor Settings: Gain: %d, Timing: %d", m.Gain, m.Timing)

	go func() {
		defer m.Disable()
		jobID := uuid.New().String()
		ticker := time.NewTicker(gnome.RECORD_INTERVAL)
		isLowLight := true

		for {
			select {
			case <-ctx.Done():
				log.Println("Job Cancelled, stopping sensor")
				return
			default:
			}

			ch0, ch1, err := m.GetFullLuminosity()
			if err != nil {
				log.Printf("Failed to get luminosity: %s", err)
				m.LuxResultsChan <- LuxResults{JobID: jobID}
				<-ticker.C
				continue
			}

			lux, err := m.CalculateLux(ch0, ch1)
			if err != nil {
				log.Printf("Failed to calculate lux: %s", err)
				if err := m.SetOptimalGain(); err != nil {
					log.Printf("Failed to set optimal gain: %s", err)
				}
				log.Printf("Updated Sensor Settings: Gain: %d, Timing: %d", m.Gain, m.Timing)
				time.Sleep(5 * time.Second)
				continue
			}

			if lux < 25 && !isLowLight {
				log.Printf("Rechecking optimal gain in low-light")
				if err := m.SetOptimalGain(); err != nil {
					log.Printf("Failed to set optimal gain: %s", err)
				}
				isLowLight = true
				log.Printf("Updated Sensor Settings: Gain: %d, Timing: %d", m.Gain, m.Timing)
			} else if lux > 25 && isLowLight {
				log.Printf("Rechecking optimal gain in high-light")
				if err := m.SetOptimalGain(); err != nil {
					log.Printf("Failed to set optimal gain: %s", err)
				}
				log.Printf("Updated Sensor Settings: Gain: %d, Timing: %d", m.Gain, m.Timing)
				isLowLight = false
			}

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

	return nil
}

// Stop the sensor
func (m *SLMeter) StopSensor() error {
	if m.TSL2591 == nil {
		return fmt.Errorf("sensor is not connected")
	}
	if !m.Enabled {
		return fmt.Errorf("sensor is already stopped")
	}

	m.cancel()
	m.Disable()
	return nil
}

// GetSignalStrength returns the signal strength of the wifi connection
func (m *SLMeter) GetSignalStrength() (SignalStrength, error) {
	cmd := exec.Command("sh", "-c", "iw dev wlan0 link | grep 'signal:' | awk '{print $2}'")
	output, err := cmd.Output()
	if err != nil {
		return SignalStrength{}, err
	}

	signalInt, err := strconv.Atoi(strings.TrimSpace(string(output)))
	if err != nil {
		return SignalStrength{}, fmt.Errorf("device is not connected to a network")
	}

	if signalInt < -110 {
		signalInt = -110
	} else if signalInt > -40 {
		signalInt = -40
	}

	strength := (signalInt + 110) * 100 / 70
	if strength < 0 {
		strength = 0
	} else if strength > 100 {
		strength = 100
	}

	return SignalStrength{
		SignalInt: signalInt,
		Strength:  strength,
	}, nil
}

// GetCurrentConditions returns the most recent sensor readings
func (m *SLMeter) GetCurrentConditions() (Conditions, error) {
	if m.TSL2591 == nil || !m.Enabled {
		return Conditions{}, nil
	}

	conditions := Conditions{}
	row := m.ResultsDB.QueryRow("SELECT job_id, lux, full_spectrum, visible, infrared FROM sunlight ORDER BY id DESC LIMIT 1")
	err := row.Scan(&conditions.JobID, &conditions.Lux, &conditions.FullSpectrum, &conditions.Visible, &conditions.Infrared)
	if err != nil {
		return Conditions{}, err
	}

	return conditions, nil
}

// GetSensorStatus returns the connection and enabled status of the sensor
func (m *SLMeter) GetSensorStatus() (Status, error) {
	status := Status{}
	if m.TSL2591 == nil {
		status.Connected = false
		return status, nil
	}

	status.Connected = true
	status.Enabled = m.Enabled
	return status, nil
}

// Read from LuxResultsChan, write the results to sqlite
func (m *SLMeter) MonitorAndRecordResults() {
	log.Println("Monitoring for new messages...")
	for {
		select {
		case result := <-m.LuxResultsChan:
			log.Println(fmt.Sprintf("- JobID: %s, Lux: %.5f", result.JobID, result.Lux))
			if math.IsInf(result.Lux, 1) {
				log.Println("Lux is invalid, skipping record")
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
