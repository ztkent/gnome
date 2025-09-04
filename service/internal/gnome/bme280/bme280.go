package bme280

/*
 * bme280 - Package for interacting with BME280 temperature, humidity, and pressure sensors.
 *
 * Supports both I2C and SPI communication protocols.
 * Based on the Bosch BME280 datasheet and reference implementations.
 */

import (
	"encoding/binary"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/sirupsen/logrus"
	"golang.org/x/exp/io/i2c"
)

var l *logrus.Logger

func init() {
	l = logrus.New()
	// Setup the logger, so it can be parsed by datadog
	l.Formatter = &logrus.JSONFormatter{}
	l.SetOutput(os.Stdout)
	// Set the log level
	logLevel := strings.ToLower(os.Getenv("LOG_LEVEL"))
	switch logLevel {
	case "debug":
		l.SetLevel(logrus.DebugLevel)
	case "info":
		l.SetLevel(logrus.InfoLevel)
	case "error":
		l.SetLevel(logrus.ErrorLevel)
	default:
		l.SetLevel(logrus.DebugLevel)
	}
}

// Calibration data structure
type calibrationData struct {
	digT1 uint16
	digT2 int16
	digT3 int16

	digP1 uint16
	digP2 int16
	digP3 int16
	digP4 int16
	digP5 int16
	digP6 int16
	digP7 int16
	digP8 int16
	digP9 int16

	digH1 uint8
	digH2 int16
	digH3 uint8
	digH4 int16
	digH5 int16
	digH6 int8

	tFine int32 // Used for pressure and humidity calculations
}

// BME280 represents the sensor device
type BME280 struct {
	Enabled      bool
	Device       *i2c.Device
	Calibration  calibrationData
	TempSampling byte
	PressSampling byte
	HumidSampling byte
	Mode         byte
	Filter       byte
	StandbyTime  byte
	*sync.Mutex
}

// EnvironmentalData represents a complete reading from the sensor
type EnvironmentalData struct {
	Temperature float64 // Celsius
	Humidity    float64 // Percent relative humidity
	Pressure    float64 // Pascals
}

// NewBME280 creates a new BME280 sensor instance via I2C
func NewBME280(i2cPath string, address int) (*BME280, error) {
	if i2cPath == "" {
		// i2c-1 is the default I2C bus for the Raspberry Pi
		i2cPath = "/dev/i2c-1"
	}
	
	if address == 0 {
		address = BME280_I2C_ADDR_PRIMARY
	}

	device, err := i2c.Open(&i2c.Devfs{Dev: i2cPath}, address)
	if err != nil {
		return nil, fmt.Errorf("failed to open I2C device: %w", err)
	}

	bme := &BME280{
		Device:        device,
		Mutex:         &sync.Mutex{},
		Enabled:       false,
		TempSampling:  BME280_SAMPLING_X1,
		PressSampling: BME280_SAMPLING_X1,
		HumidSampling: BME280_SAMPLING_X1,
		Mode:          BME280_MODE_NORMAL,
		Filter:        BME280_FILTER_OFF,
		StandbyTime:   BME280_STANDBY_MS_1000,
	}

	// Read and verify chip ID
	if err := bme.verifyChipID(); err != nil {
		return nil, err
	}

	// Soft reset the sensor
	if err := bme.reset(); err != nil {
		return nil, err
	}

	// Read calibration data
	if err := bme.readCalibrationData(); err != nil {
		return nil, err
	}

	// Configure the sensor with default settings
	if err := bme.configure(); err != nil {
		return nil, err
	}

	return bme, nil
}

// verifyChipID reads and verifies the BME280 chip ID
func (bme *BME280) verifyChipID() error {
	buf := make([]byte, 1)
	err := bme.Device.ReadReg(BME280_REGISTER_CHIPID, buf)
	if err != nil {
		return fmt.Errorf("failed to read chip ID: %w", err)
	}
	
	if buf[0] != BME280_CHIPID {
		return fmt.Errorf("invalid chip ID: expected 0x%02X, got 0x%02X", BME280_CHIPID, buf[0])
	}
	
	return nil
}

// reset performs a soft reset of the sensor
func (bme *BME280) reset() error {
	return bme.Device.WriteReg(BME280_REGISTER_SOFTRESET, []byte{0xB6})
}

// readCalibrationData reads all calibration coefficients from the sensor
func (bme *BME280) readCalibrationData() error {
	// Temperature calibration data (0x88-0x8D)
	tempData := make([]byte, 6)
	if err := bme.Device.ReadReg(BME280_REGISTER_DIG_T1, tempData); err != nil {
		return fmt.Errorf("failed to read temperature calibration: %w", err)
	}
	
	bme.Calibration.digT1 = binary.LittleEndian.Uint16(tempData[0:2])
	bme.Calibration.digT2 = int16(binary.LittleEndian.Uint16(tempData[2:4]))
	bme.Calibration.digT3 = int16(binary.LittleEndian.Uint16(tempData[4:6]))

	// Pressure calibration data (0x8E-0x9F)
	pressData := make([]byte, 18)
	if err := bme.Device.ReadReg(BME280_REGISTER_DIG_P1, pressData); err != nil {
		return fmt.Errorf("failed to read pressure calibration: %w", err)
	}
	
	bme.Calibration.digP1 = binary.LittleEndian.Uint16(pressData[0:2])
	bme.Calibration.digP2 = int16(binary.LittleEndian.Uint16(pressData[2:4]))
	bme.Calibration.digP3 = int16(binary.LittleEndian.Uint16(pressData[4:6]))
	bme.Calibration.digP4 = int16(binary.LittleEndian.Uint16(pressData[6:8]))
	bme.Calibration.digP5 = int16(binary.LittleEndian.Uint16(pressData[8:10]))
	bme.Calibration.digP6 = int16(binary.LittleEndian.Uint16(pressData[10:12]))
	bme.Calibration.digP7 = int16(binary.LittleEndian.Uint16(pressData[12:14]))
	bme.Calibration.digP8 = int16(binary.LittleEndian.Uint16(pressData[14:16]))
	bme.Calibration.digP9 = int16(binary.LittleEndian.Uint16(pressData[16:18]))

	// Humidity calibration data H1 (0xA1)
	h1Data := make([]byte, 1)
	if err := bme.Device.ReadReg(BME280_REGISTER_DIG_H1, h1Data); err != nil {
		return fmt.Errorf("failed to read humidity calibration H1: %w", err)
	}
	bme.Calibration.digH1 = h1Data[0]

	// Humidity calibration data H2-H6 (0xE1-0xE7)
	humidData := make([]byte, 7)
	if err := bme.Device.ReadReg(BME280_REGISTER_DIG_H2, humidData); err != nil {
		return fmt.Errorf("failed to read humidity calibration H2-H6: %w", err)
	}
	
	bme.Calibration.digH2 = int16(binary.LittleEndian.Uint16(humidData[0:2]))
	bme.Calibration.digH3 = humidData[2]
	bme.Calibration.digH4 = int16(humidData[3])<<4 | int16(humidData[4]&0x0F)
	bme.Calibration.digH5 = int16(humidData[5])<<4 | int16(humidData[4])>>4
	bme.Calibration.digH6 = int8(humidData[6])

	return nil
}

// configure sets up the sensor with the current configuration
func (bme *BME280) configure() error {
	// Set humidity sampling first (must be done before ctrl_meas)
	humidCtrl := bme.HumidSampling
	if err := bme.Device.WriteReg(BME280_REGISTER_CONTROLHUMID, []byte{humidCtrl}); err != nil {
		return fmt.Errorf("failed to set humidity control: %w", err)
	}

	// Set config register (standby time, filter, SPI interface)
	config := (bme.StandbyTime << 5) | (bme.Filter << 2)
	if err := bme.Device.WriteReg(BME280_REGISTER_CONFIG, []byte{config}); err != nil {
		return fmt.Errorf("failed to set config register: %w", err)
	}

	// Set ctrl_meas register (pressure sampling, temperature sampling, mode)
	ctrlMeas := (bme.TempSampling << 5) | (bme.PressSampling << 2) | bme.Mode
	if err := bme.Device.WriteReg(BME280_REGISTER_CONTROL, []byte{ctrlMeas}); err != nil {
		return fmt.Errorf("failed to set control register: %w", err)
	}

	bme.Enabled = true
	return nil
}

// Enable turns on the sensor
func (bme *BME280) Enable() error {
	bme.Lock()
	defer bme.Unlock()

	if bme.Enabled {
		return nil
	}

	return bme.configure()
}

// Disable turns off the sensor
func (bme *BME280) Disable() error {
	bme.Lock()
	defer bme.Unlock()

	if !bme.Enabled {
		return nil
	}

	// Set to sleep mode
	ctrlMeas := (bme.TempSampling << 5) | (bme.PressSampling << 2) | BME280_MODE_SLEEP
	if err := bme.Device.WriteReg(BME280_REGISTER_CONTROL, []byte{ctrlMeas}); err != nil {
		return fmt.Errorf("failed to disable sensor: %w", err)
	}

	bme.Enabled = false
	return nil
}

// ReadSensorData reads temperature, humidity, and pressure from the sensor
func (bme *BME280) ReadSensorData() (EnvironmentalData, error) {
	if !bme.Enabled {
		return EnvironmentalData{}, errors.New("sensor must be enabled")
	}

	bme.Lock()
	defer bme.Unlock()

	// If in forced mode, trigger a measurement
	if bme.Mode == BME280_MODE_FORCED {
		ctrlMeas := (bme.TempSampling << 5) | (bme.PressSampling << 2) | BME280_MODE_FORCED
		if err := bme.Device.WriteReg(BME280_REGISTER_CONTROL, []byte{ctrlMeas}); err != nil {
			return EnvironmentalData{}, fmt.Errorf("failed to trigger measurement: %w", err)
		}

		// Wait for measurement to complete
		time.Sleep(100 * time.Millisecond)
	}

	// Read all sensor data (pressure, temperature, humidity)
	data := make([]byte, 8)
	if err := bme.Device.ReadReg(BME280_REGISTER_PRESSUREDATA, data); err != nil {
		return EnvironmentalData{}, fmt.Errorf("failed to read sensor data: %w", err)
	}

	// Extract raw values
	rawPress := int32(data[0])<<12 | int32(data[1])<<4 | int32(data[2])>>4
	rawTemp := int32(data[3])<<12 | int32(data[4])<<4 | int32(data[5])>>4
	rawHumid := int32(data[6])<<8 | int32(data[7])

	// Calculate compensated values
	temperature := bme.compensateTemperature(rawTemp)
	pressure := bme.compensatePressure(rawPress)
	humidity := bme.compensateHumidity(rawHumid)

	return EnvironmentalData{
		Temperature: temperature,
		Humidity:    humidity,
		Pressure:    pressure,
	}, nil
}

// compensateTemperature calculates the compensated temperature value
func (bme *BME280) compensateTemperature(rawTemp int32) float64 {
	var1 := ((rawTemp >> 3) - (int32(bme.Calibration.digT1) << 1)) * int32(bme.Calibration.digT2) >> 11
	var2 := (((rawTemp >> 4) - int32(bme.Calibration.digT1)) * ((rawTemp >> 4) - int32(bme.Calibration.digT1)) >> 12) * int32(bme.Calibration.digT3) >> 14

	bme.Calibration.tFine = var1 + var2
	temperature := (bme.Calibration.tFine*5 + 128) >> 8

	return float64(temperature) / 100.0
}

// compensatePressure calculates the compensated pressure value
func (bme *BME280) compensatePressure(rawPress int32) float64 {
	var1 := int64(bme.Calibration.tFine) - 128000
	var2 := var1 * var1 * int64(bme.Calibration.digP6)
	var2 = var2 + ((var1 * int64(bme.Calibration.digP5)) << 17)
	var2 = var2 + (int64(bme.Calibration.digP4) << 35)
	var1 = ((var1 * var1 * int64(bme.Calibration.digP3)) >> 8) + ((var1 * int64(bme.Calibration.digP2)) << 12)
	var1 = (((int64(1) << 47) + var1) * int64(bme.Calibration.digP1)) >> 33

	if var1 == 0 {
		return 0
	}

	pressure := int64(1048576 - rawPress)
	pressure = (((pressure << 31) - var2) * 3125) / var1
	var1 = (int64(bme.Calibration.digP9) * (pressure >> 13) * (pressure >> 13)) >> 25
	var2 = (int64(bme.Calibration.digP8) * pressure) >> 19

	pressure = ((pressure + var1 + var2) >> 8) + (int64(bme.Calibration.digP7) << 4)

	return float64(pressure) / 256.0
}

// compensateHumidity calculates the compensated humidity value
func (bme *BME280) compensateHumidity(rawHumid int32) float64 {
	vX1 := bme.Calibration.tFine - 76800

	vX1 = (((((rawHumid << 14) - (int32(bme.Calibration.digH4) << 20) -
		(int32(bme.Calibration.digH5) * vX1)) + 16384) >> 15) *
		(((((((vX1 * int32(bme.Calibration.digH6)) >> 10) *
			(((vX1 * int32(bme.Calibration.digH3)) >> 11) + 32768)) >> 10) +
			2097152) * int32(bme.Calibration.digH2) + 8192) >> 14))

	vX1 = vX1 - (((((vX1 >> 15) * (vX1 >> 15)) >> 7) * int32(bme.Calibration.digH1)) >> 4)

	if vX1 < 0 {
		vX1 = 0
	}
	if vX1 > 419430400 {
		vX1 = 419430400
	}

	humidity := vX1 >> 12

	return float64(humidity) / 1024.0
}

// SetSamplingMode configures the sampling rates for all measurements
func (bme *BME280) SetSamplingMode(tempSampling, pressSampling, humidSampling byte) error {
	bme.Lock()
	defer bme.Unlock()

	bme.TempSampling = tempSampling
	bme.PressSampling = pressSampling
	bme.HumidSampling = humidSampling

	if bme.Enabled {
		return bme.configure()
	}
	return nil
}

// SetFilter configures the IIR filter
func (bme *BME280) SetFilter(filter byte) error {
	bme.Lock()
	defer bme.Unlock()

	bme.Filter = filter

	if bme.Enabled {
		return bme.configure()
	}
	return nil
}

// SetStandbyTime configures the standby time for normal mode
func (bme *BME280) SetStandbyTime(standbyTime byte) error {
	bme.Lock()
	defer bme.Unlock()

	bme.StandbyTime = standbyTime

	if bme.Enabled {
		return bme.configure()
	}
	return nil
}

// GetStatus returns a string representation of the current sensor mode
func (bme *BME280) GetStatus() string {
	switch bme.Mode {
	case BME280_MODE_SLEEP:
		return "Sleep"
	case BME280_MODE_FORCED:
		return "Forced"
	case BME280_MODE_NORMAL:
		return "Normal"
	default:
		return "Unknown"
	}
}