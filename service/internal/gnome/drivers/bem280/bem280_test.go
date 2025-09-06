package bem280

import (
	"fmt"
	"log"
	"testing"
	"time"
)

// TestNewBME280 tests the basic initialization of the BME280 sensor
func TestNewBME280(t *testing.T) {
	// Test with default settings
	bme, err := NewBME280(BME280_OVERSAMPLING_1X, BME280_FILTER_COEFF_OFF, "")
	if err != nil {
		t.Logf("Note: BME280 initialization failed (expected if no hardware): %v", err)
		// This is expected if running without actual hardware
		return
	}
	defer bme.Close()

	// Verify chip ID
	chipID, err := bme.GetChipID()
	if err != nil {
		t.Errorf("Failed to get chip ID: %v", err)
		return
	}

	if chipID != BME280_CHIP_ID {
		t.Errorf("Invalid chip ID: expected 0x%02X, got 0x%02X", BME280_CHIP_ID, chipID)
	}

	t.Logf("BME280 successfully initialized with chip ID: 0x%02X", chipID)
}

// TestBME280BasicFunctionality tests basic sensor operations
func TestBME280BasicFunctionality(t *testing.T) {
	// Try to create BME280 instance
	bme, err := NewBME280(BME280_OVERSAMPLING_2X, BME280_FILTER_COEFF_2, "")
	if err != nil {
		t.Logf("Skipping test - BME280 hardware not available: %v", err)
		return
	}
	defer bme.Close()

	t.Log("Testing basic BME280 functionality...")

	// Test power mode operations
	t.Log("Testing power mode operations...")
	
	// Get current power mode
	currentMode, err := bme.GetPowerMode()
	if err != nil {
		t.Errorf("Failed to get power mode: %v", err)
		return
	}
	t.Logf("Current power mode: 0x%02X", currentMode)

	// Test setting power modes
	modes := []byte{BME280_POWERMODE_SLEEP, BME280_POWERMODE_FORCED, BME280_POWERMODE_NORMAL}
	modeNames := []string{"Sleep", "Forced", "Normal"}
	
	for i, mode := range modes {
		err := bme.SetPowerMode(mode)
		if err != nil {
			t.Errorf("Failed to set power mode %s: %v", modeNames[i], err)
			continue
		}
		
		// Verify the mode was set
		actualMode, err := bme.GetPowerMode()
		if err != nil {
			t.Errorf("Failed to read power mode: %v", err)
			continue
		}
		
		if actualMode != mode {
			t.Errorf("Power mode mismatch: expected 0x%02X, got 0x%02X", mode, actualMode)
		} else {
			t.Logf("Successfully set power mode to %s (0x%02X)", modeNames[i], mode)
		}
		
		time.Sleep(10 * time.Millisecond)
	}

	// Test sensor status
	status, err := bme.GetStatus()
	if err != nil {
		t.Errorf("Failed to get sensor status: %v", err)
	} else {
		t.Logf("Sensor status: 0x%02X", status)
		if status&BME280_STATUS_IM_UPDATE != 0 {
			t.Log("NVM update in progress")
		}
		if status&BME280_STATUS_MEAS_DONE != 0 {
			t.Log("Measurement completed")
		}
	}

	// Test calibration data
	calibData := bme.GetCalibrationData()
	t.Logf("Calibration data - T1: %d, T2: %d, T3: %d", calibData.DigT1, calibData.DigT2, calibData.DigT3)
	t.Logf("Calibration data - P1: %d, P2: %d, P3: %d", calibData.DigP1, calibData.DigP2, calibData.DigP3)
	t.Logf("Calibration data - H1: %d, H2: %d, H3: %d", calibData.DigH1, calibData.DigH2, calibData.DigH3)

	// Test measurement timing calculation
	measureTime := bme.GetMeasurementTime()
	t.Logf("Calculated measurement time: %d μs", measureTime)

	// Test settings
	settings := bme.GetSettings()
	t.Logf("Current settings - P: %d, T: %d, H: %d, Filter: %d, Standby: %d",
		settings.OversamplingP, settings.OversamplingT, settings.OversamplingH,
		settings.Filter, settings.StandbyTime)
}

// TestBME280SensorReadings tests actual sensor data reading
func TestBME280SensorReadings(t *testing.T) {
	bme, err := NewBME280(BME280_OVERSAMPLING_4X, BME280_FILTER_COEFF_4, "")
	if err != nil {
		t.Logf("Skipping sensor readings test - BME280 hardware not available: %v", err)
		return
	}
	defer bme.Close()

	t.Log("Testing BME280 sensor readings...")

	// Test individual sensor readings
	t.Log("Reading individual sensors...")
	
	temp, err := bme.ReadTemperature()
	if err != nil {
		t.Errorf("Failed to read temperature: %v", err)
	} else {
		t.Logf("Temperature: %.2f°C", temp)
		// Basic sanity check
		if temp < -50 || temp > 100 {
			t.Errorf("Temperature reading seems unrealistic: %.2f°C", temp)
		}
	}

	pressure, err := bme.ReadPressure()
	if err != nil {
		t.Errorf("Failed to read pressure: %v", err)
	} else {
		t.Logf("Pressure: %.2f Pa (%.2f hPa)", pressure, pressure/100.0)
		// Basic sanity check
		if pressure < 50000 || pressure > 120000 {
			t.Errorf("Pressure reading seems unrealistic: %.2f Pa", pressure)
		}
	}

	humidity, err := bme.ReadHumidity()
	if err != nil {
		t.Errorf("Failed to read humidity: %v", err)
	} else {
		t.Logf("Humidity: %.1f%%", humidity)
		// Basic sanity check
		if humidity < 0 || humidity > 100 {
			t.Errorf("Humidity reading out of range: %.1f%%", humidity)
		}
	}

	// Test reading all sensors at once
	t.Log("Reading all sensors at once...")
	data, err := bme.ReadAll()
	if err != nil {
		t.Errorf("Failed to read all sensor data: %v", err)
	} else {
		t.Logf("All sensors - T: %.2f°C, P: %d Pa, H: %.1f%%",
			data.Temperature, data.Pressure, data.Humidity)
	}

	// Test altitude calculation
	if pressure > 0 {
		altitude, err := bme.CalculateAltitude(101325) // Standard sea level pressure
		if err != nil {
			t.Errorf("Failed to calculate altitude: %v", err)
		} else {
			t.Logf("Calculated altitude: %.1f m", altitude)
		}
	}

	// Test raw data reading
	rawData, err := bme.ReadRawData()
	if err != nil {
		t.Errorf("Failed to read raw data: %v", err)
	} else {
		t.Logf("Raw data - T: %d, P: %d, H: %d",
			rawData.Temperature, rawData.Pressure, rawData.Humidity)
	}
}

// TestBME280Configuration tests various configuration options
func TestBME280Configuration(t *testing.T) {
	bme, err := NewBME280(BME280_OVERSAMPLING_1X, BME280_FILTER_COEFF_OFF, "")
	if err != nil {
		t.Logf("Skipping configuration test - BME280 hardware not available: %v", err)
		return
	}
	defer bme.Close()

	t.Log("Testing BME280 configuration options...")

	// Test oversampling settings
	oversamplingModes := []byte{
		BME280_NO_OVERSAMPLING,
		BME280_OVERSAMPLING_1X,
		BME280_OVERSAMPLING_2X,
		BME280_OVERSAMPLING_4X,
		BME280_OVERSAMPLING_8X,
		BME280_OVERSAMPLING_16X,
	}

	for _, mode := range oversamplingModes {
		err := bme.SetOversamplingSettings(mode, mode, mode)
		if err != nil {
			t.Errorf("Failed to set oversampling to %dx: %v", mode, err)
			continue
		}
		
		settings := bme.GetSettings()
		if settings.OversamplingP != mode || settings.OversamplingT != mode || settings.OversamplingH != mode {
			t.Errorf("Oversampling settings not applied correctly")
		} else {
			t.Logf("Successfully set oversampling to %dx", mode)
		}
	}

	// Test filter settings
	filterCoeffs := []byte{
		BME280_FILTER_COEFF_OFF,
		BME280_FILTER_COEFF_2,
		BME280_FILTER_COEFF_4,
		BME280_FILTER_COEFF_8,
		BME280_FILTER_COEFF_16,
	}

	for _, coeff := range filterCoeffs {
		err := bme.SetFilterSettings(coeff)
		if err != nil {
			t.Errorf("Failed to set filter coefficient to %d: %v", coeff, err)
			continue
		}
		
		settings := bme.GetSettings()
		if settings.Filter != coeff {
			t.Errorf("Filter setting not applied correctly")
		} else {
			t.Logf("Successfully set filter coefficient to %d", coeff)
		}
	}

	// Test standby time settings
	standbyTimes := []byte{
		BME280_STANDBY_TIME_0_5_MS,
		BME280_STANDBY_TIME_62_5_MS,
		BME280_STANDBY_TIME_125_MS,
		BME280_STANDBY_TIME_250_MS,
		BME280_STANDBY_TIME_500_MS,
		BME280_STANDBY_TIME_1000_MS,
	}

	for _, standby := range standbyTimes {
		err := bme.SetStandbyTime(standby)
		if err != nil {
			t.Errorf("Failed to set standby time: %v", err)
			continue
		}
		
		settings := bme.GetSettings()
		if settings.StandbyTime != standby {
			t.Errorf("Standby time setting not applied correctly")
		} else {
			t.Logf("Successfully set standby time setting")
		}
	}
}

// TestBME280ErrorHandling tests error conditions and validation
func TestBME280ErrorHandling(t *testing.T) {
	bme, err := NewBME280(BME280_OVERSAMPLING_1X, BME280_FILTER_COEFF_OFF, "")
	if err != nil {
		t.Logf("Skipping error handling test - BME280 hardware not available: %v", err)
		return
	}
	defer bme.Close()

	t.Log("Testing BME280 error handling...")

	// Test invalid power mode
	err = bme.SetPowerMode(0xFF)
	if err == nil {
		t.Error("Should have failed with invalid power mode")
	} else {
		t.Logf("Correctly rejected invalid power mode: %v", err)
	}

	// Test invalid oversampling settings
	err = bme.SetOversamplingSettings(0xFF, BME280_OVERSAMPLING_1X, BME280_OVERSAMPLING_1X)
	if err == nil {
		t.Error("Should have failed with invalid oversampling setting")
	} else {
		t.Logf("Correctly rejected invalid oversampling: %v", err)
	}

	// Test invalid filter coefficient
	err = bme.SetFilterSettings(0xFF)
	if err == nil {
		t.Error("Should have failed with invalid filter coefficient")
	} else {
		t.Logf("Correctly rejected invalid filter coefficient: %v", err)
	}

	// Test invalid standby time
	err = bme.SetStandbyTime(0xFF)
	if err == nil {
		t.Error("Should have failed with invalid standby time")
	} else {
		t.Logf("Correctly rejected invalid standby time: %v", err)
	}
}

// BenchmarkBME280Reading benchmarks sensor reading performance
func BenchmarkBME280Reading(b *testing.B) {
	bme, err := NewBME280(BME280_OVERSAMPLING_1X, BME280_FILTER_COEFF_OFF, "")
	if err != nil {
		b.Skipf("BME280 hardware not available: %v", err)
	}
	defer bme.Close()

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_, err := bme.ReadAll()
		if err != nil {
			b.Fatalf("Failed to read sensor data: %v", err)
		}
	}
}

// ExampleBME280_basic demonstrates basic BME280 usage
func ExampleBME280_basic() {
	// Create BME280 instance with 2x oversampling and filter coefficient 4
	bme, err := NewBME280(BME280_OVERSAMPLING_2X, BME280_FILTER_COEFF_4, "")
	if err != nil {
		log.Fatal(err)
	}
	defer bme.Close()

	// Read all sensor data
	data, err := bme.ReadAll()
	if err != nil {
		log.Fatal(err)
	}

	fmt.Printf("Temperature: %.2f°C\n", data.Temperature)
	fmt.Printf("Pressure: %d Pa (%.2f hPa)\n", data.Pressure, float32(data.Pressure)/100.0)
	fmt.Printf("Humidity: %.1f%%\n", data.Humidity)

	// Calculate altitude
	altitude, err := bme.CalculateAltitude(101325) // Sea level pressure in Pa
	if err != nil {
		log.Fatal(err)
	}
	fmt.Printf("Altitude: %.1f m\n", altitude)
}

// ExampleBME280_advanced demonstrates advanced BME280 configuration
func ExampleBME280_advanced() {
	// Create BME280 instance
	bme, err := NewBME280(BME280_OVERSAMPLING_1X, BME280_FILTER_COEFF_OFF, "")
	if err != nil {
		log.Fatal(err)
	}
	defer bme.Close()

	// Configure high precision settings
	err = bme.SetOversamplingSettings(
		BME280_OVERSAMPLING_16X, // Pressure: highest precision
		BME280_OVERSAMPLING_2X,  // Temperature: moderate precision  
		BME280_OVERSAMPLING_1X,  // Humidity: standard precision
	)
	if err != nil {
		log.Fatal(err)
	}

	// Enable IIR filter for stable readings
	err = bme.SetFilterSettings(BME280_FILTER_COEFF_16)
	if err != nil {
		log.Fatal(err)
	}

	// Set normal mode with 125ms standby time
	err = bme.SetStandbyTime(BME280_STANDBY_TIME_125_MS)
	if err != nil {
		log.Fatal(err)
	}

	err = bme.SetPowerMode(BME280_POWERMODE_NORMAL)
	if err != nil {
		log.Fatal(err)
	}

	// Read measurements
	for i := 0; i < 5; i++ {
		data, err := bme.ReadAll()
		if err != nil {
			log.Fatal(err)
		}

		fmt.Printf("Reading %d: T=%.2f°C P=%d Pa H=%.1f%%\n",
			i+1, data.Temperature, data.Pressure, data.Humidity)

		time.Sleep(200 * time.Millisecond)
	}
}