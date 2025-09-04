package gnome

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
	"github.com/ztkent/gnome/internal/gnome/bme280"
	"github.com/ztkent/gnome/internal/gnome/tsl2591"
)

const (
	MAX_JOB_DURATION = 168 * time.Hour
	RECORD_INTERVAL  = 15 * time.Second
	GNOME_DB_PATH    = "gnome.db"
	GNOME_CSV_PATH   = "gnome.csv"
)

type SLMeter struct {
	*tsl2591.TSL2591
	*bme280.BME280
	LuxResultsChan         chan LuxResults
	EnvironmentalResultsChan chan EnvironmentalResults
	ResultsDB              *sql.DB
	cancel                 context.CancelFunc
	cancelEnvironmental    context.CancelFunc
	Pid                    int
}

type LuxResults struct {
	Lux          float64
	Infrared     float64
	Visible      float64
	FullSpectrum float64
	JobID        string
}

type EnvironmentalResults struct {
	Temperature float64
	Humidity    float64
	Pressure    float64
	JobID       string
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

type EnvironmentalConditions struct {
	JobID       string  `json:"jobID"`
	Temperature float64 `json:"temperature"`
	Humidity    float64 `json:"humidity"`
	Pressure    float64 `json:"pressure"`
}

type Status struct {
	Connected bool `json:"connected"`
	Enabled   bool `json:"enabled"`
}

type EnvironmentalStatus struct {
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
	if m.TSL2591.Enabled {
		return fmt.Errorf("sensor is already started")
	}

	// Create context with timeout
	ctx, cancel := context.WithCancel(context.Background()) // Lets let it run forever
	m.cancel = cancel

	// Enable sensor
	m.TSL2591.Enable()

	// Initializing Sensor Optimal Gain
	log.Printf("Setting sensor initial gain & integration time")
	if err := m.SetOptimalGain(); err != nil {
		log.Printf("Failed to set initial optimal gain: %s, using default settings", err)
	}
	log.Printf("Current Sensor Settings: Gain: %s, Timing: %s", m.GetGain(), m.GetTiming())

	go func() {
		defer m.TSL2591.Disable()
		jobID := uuid.New().String()
		ticker := time.NewTicker(RECORD_INTERVAL)
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
				log.Printf("Updated Sensor Settings: Gain: %s, Timing: %s", m.GetGain(), m.GetTiming())
				time.Sleep(5 * time.Second)
				continue
			} else if math.IsInf(lux, 0) {
				log.Printf("Lux is +inf, the sensor is over/under saturated, rechecking optimal gain")
				if err := m.SetOptimalGain(); err != nil {
					log.Printf("Failed to set optimal gain: %s", err)
				}
				log.Printf("Updated Sensor Settings: Gain: %s, Timing: %s", m.GetGain(), m.GetTiming())
				time.Sleep(5 * time.Second)
				continue
			}

			if lux < 25 && !isLowLight {
				log.Printf("Rechecking optimal gain in low-light")
				if err := m.SetOptimalGain(); err != nil {
					log.Printf("Failed to set optimal gain: %s", err)
				}
				isLowLight = true
				log.Printf("Updated Sensor Settings: Gain: %s, Timing: %s", m.GetGain(), m.GetTiming())
			} else if lux > 25 && isLowLight {
				log.Printf("Rechecking optimal gain in high-light")
				if err := m.SetOptimalGain(); err != nil {
					log.Printf("Failed to set optimal gain: %s", err)
				}
				isLowLight = false
				log.Printf("Updated Sensor Settings: Gain: %s, Timing: %s", m.GetGain(), m.GetTiming())
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
	if !m.TSL2591.Enabled {
		return fmt.Errorf("sensor is already stopped")
	}

	m.cancel()
	m.TSL2591.Disable()
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
	if m.TSL2591 == nil || !m.TSL2591.Enabled {
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
	status.Enabled = m.TSL2591.Enabled
	return status, nil
}

// Read from LuxResultsChan, write the results to sqlite
func (m *SLMeter) MonitorAndRecordResults() {
	log.Println("Monitoring for new messages...")
	for result := range m.LuxResultsChan {
		log.Printf("- JobID: %s, Lux: %.5f", result.JobID, result.Lux)
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

// StartEnvironmentalSensor starts the BME280 sensor and collects environmental data
func (m *SLMeter) StartEnvironmentalSensor() error {
	if m.BME280 == nil {
		return fmt.Errorf("environmental sensor is not connected")
	}
	if m.BME280.Enabled {
		return fmt.Errorf("environmental sensor is already started")
	}

	// Create context with cancellation
	ctx, cancel := context.WithCancel(context.Background())
	m.cancelEnvironmental = cancel

	// Enable sensor
	if err := m.BME280.Enable(); err != nil {
		return fmt.Errorf("failed to enable environmental sensor: %w", err)
	}

	log.Printf("Starting environmental sensor monitoring")

	go func() {
		defer m.BME280.Disable()
		jobID := uuid.New().String()
		ticker := time.NewTicker(RECORD_INTERVAL)
		defer ticker.Stop()

		for {
			select {
			case <-ctx.Done():
				log.Println("Environmental sensor job cancelled, stopping sensor")
				return
			default:
			}

			data, err := m.BME280.ReadSensorData()
			if err != nil {
				log.Printf("Failed to read environmental data: %s", err)
				m.EnvironmentalResultsChan <- EnvironmentalResults{JobID: jobID}
				<-ticker.C
				continue
			}

			m.EnvironmentalResultsChan <- EnvironmentalResults{
				Temperature: data.Temperature,
				Humidity:    data.Humidity,
				Pressure:    data.Pressure,
				JobID:       jobID,
			}
			<-ticker.C
		}
	}()

	return nil
}

// StopEnvironmentalSensor stops the BME280 sensor
func (m *SLMeter) StopEnvironmentalSensor() error {
	if m.BME280 == nil {
		return fmt.Errorf("environmental sensor is not connected")
	}
	if !m.BME280.Enabled {
		return fmt.Errorf("environmental sensor is already stopped")
	}

	if m.cancelEnvironmental != nil {
		m.cancelEnvironmental()
	}
	return m.BME280.Disable()
}

// GetCurrentEnvironmentalConditions returns the most recent environmental sensor readings
func (m *SLMeter) GetCurrentEnvironmentalConditions() (EnvironmentalConditions, error) {
	if m.BME280 == nil || !m.BME280.Enabled {
		return EnvironmentalConditions{}, nil
	}

	conditions := EnvironmentalConditions{}
	row := m.ResultsDB.QueryRow("SELECT job_id, temperature, humidity, pressure FROM environmental ORDER BY id DESC LIMIT 1")
	err := row.Scan(&conditions.JobID, &conditions.Temperature, &conditions.Humidity, &conditions.Pressure)
	if err != nil {
		return EnvironmentalConditions{}, err
	}

	return conditions, nil
}

// GetEnvironmentalSensorStatus returns the connection and enabled status of the environmental sensor
func (m *SLMeter) GetEnvironmentalSensorStatus() (EnvironmentalStatus, error) {
	status := EnvironmentalStatus{}
	if m.BME280 == nil {
		status.Connected = false
		return status, nil
	}

	status.Connected = true
	status.Enabled = m.BME280.Enabled
	return status, nil
}

// MonitorAndRecordEnvironmentalResults reads from EnvironmentalResultsChan and writes to sqlite
func (m *SLMeter) MonitorAndRecordEnvironmentalResults() {
	log.Println("Monitoring for new environmental messages...")
	for result := range m.EnvironmentalResultsChan {
		log.Printf("- JobID: %s, Temp: %.2f°C, Humidity: %.2f%%, Pressure: %.2f Pa", 
			result.JobID, result.Temperature, result.Humidity, result.Pressure)
		
		if math.IsInf(result.Temperature, 0) || math.IsInf(result.Humidity, 0) || math.IsInf(result.Pressure, 0) {
			log.Println("Environmental data is invalid, skipping record")
			continue
		}
		
		_, err := m.ResultsDB.Exec(
			"INSERT INTO environmental (job_id, temperature, humidity, pressure) VALUES (?, ?, ?, ?)",
			result.JobID,
			fmt.Sprintf("%.2f", result.Temperature),
			fmt.Sprintf("%.2f", result.Humidity),
			fmt.Sprintf("%.2f", result.Pressure),
		)
		if err != nil {
			log.Println(err)
		}
	}
}
