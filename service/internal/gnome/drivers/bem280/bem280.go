package bem280

import (
	"errors"
	"fmt"
	"math"
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

type BME280 struct {
	Device *i2c.Device
	*sync.Mutex
	Enabled         bool
	CalibrationData CalibrationData
	Settings        SensorSettings
	ChipID          byte
}

// NewBME280 creates a new BME280 sensor instance
// oversamplingSettings: byte containing combined oversampling settings (use constants)
// filterCoeff: IIR filter coefficient (use BME280_FILTER_COEFF_* constants)
// path: I2C device path (empty string uses default "/dev/i2c-2")
func NewBME280(oversamplingSettings byte, filterCoeff byte, path string) (*BME280, error) {
	if path == "" {
		// i2c-2 is a virtual I2C bus that we've configured
		path = "/dev/i2c-2"
	}

	device, err := i2c.Open(&i2c.Devfs{Dev: path}, int(BME280_ADDR))
	if err != nil {
		return nil, fmt.Errorf("failed to open I2C device: %w", err)
	}

	bme := &BME280{
		Device:  device,
		Mutex:   &sync.Mutex{},
		Enabled: true,
		Settings: SensorSettings{
			OversamplingP: BME280_OVERSAMPLING_1X,
			OversamplingT: BME280_OVERSAMPLING_1X,
			OversamplingH: BME280_OVERSAMPLING_1X,
			Filter:        filterCoeff,
			StandbyTime:   BME280_STANDBY_TIME_125_MS,
		},
	}

	// Initialize the sensor
	if err := bme.Init(); err != nil {
		device.Close()
		return nil, fmt.Errorf("failed to initialize BME280: %w", err)
	}

	l.Info("BME280 sensor initialized successfully")
	return bme, nil
}

// Init initializes the BME280 sensor
func (b *BME280) Init() error {
	b.Lock()
	defer b.Unlock()

	// Read and verify chip ID
	chipID, err := b.getChipID()
	if err != nil {
		return fmt.Errorf("failed to read chip ID: %w", err)
	}

	if chipID != BME280_CHIP_ID {
		return fmt.Errorf("invalid chip ID: expected 0x%02X, got 0x%02X", BME280_CHIP_ID, chipID)
	}

	b.ChipID = chipID
	l.Debugf("BME280 chip ID verified: 0x%02X", chipID)

	// Perform soft reset
	if err := b.softReset(); err != nil {
		return fmt.Errorf("failed to perform soft reset: %w", err)
	}

	// Wait for reset to complete
	time.Sleep(10 * time.Millisecond)

	// Read calibration data
	if err := b.readCalibrationData(); err != nil {
		return fmt.Errorf("failed to read calibration data: %w", err)
	}

	// Apply default settings
	if err := b.applySettings(); err != nil {
		return fmt.Errorf("failed to apply settings: %w", err)
	}

	l.Info("BME280 initialization completed")
	return nil
}

// Close cleanly shuts down the BME280 sensor
func (b *BME280) Close() error {
	b.Lock()
	defer b.Unlock()

	if b.Device != nil {
		// Put sensor to sleep before closing
		if err := b.setPowerMode(BME280_POWERMODE_SLEEP); err != nil {
			l.Warnf("Failed to put BME280 to sleep: %v", err)
		}

		if err := b.Device.Close(); err != nil {
			return fmt.Errorf("failed to close I2C device: %w", err)
		}
		b.Device = nil
	}

	b.Enabled = false
	l.Info("BME280 sensor closed")
	return nil
}

// GetChipID returns the chip ID
func (b *BME280) GetChipID() (byte, error) {
	b.Lock()
	defer b.Unlock()
	return b.getChipID()
}

func (b *BME280) getChipID() (byte, error) {
	buf := make([]byte, 1)
	if err := b.Device.ReadReg(BME280_REG_CHIP_ID, buf); err != nil {
		return 0, err
	}
	return buf[0], nil
}

// SoftReset performs a soft reset of the sensor
func (b *BME280) SoftReset() error {
	b.Lock()
	defer b.Unlock()
	return b.softReset()
}

func (b *BME280) softReset() error {
	resetCmd := []byte{BME280_SOFT_RESET_COMMAND}
	if err := b.Device.WriteReg(BME280_REG_RESET, resetCmd); err != nil {
		return err
	}

	// Wait for reset to complete and NVM to copy
	time.Sleep(5 * time.Millisecond)

	// Wait for status to indicate NVM copy is complete
	for i := 0; i < 10; i++ {
		status, err := b.getStatus()
		if err != nil {
			return err
		}
		if status&BME280_STATUS_IM_UPDATE == 0 {
			break
		}
		time.Sleep(1 * time.Millisecond)
	}

	return nil
}

// GetStatus returns the current sensor status
func (b *BME280) GetStatus() (byte, error) {
	b.Lock()
	defer b.Unlock()
	return b.getStatus()
}

func (b *BME280) getStatus() (byte, error) {
	buf := make([]byte, 1)
	if err := b.Device.ReadReg(BME280_REG_STATUS, buf); err != nil {
		return 0, err
	}
	return buf[0], nil
}

// SetPowerMode sets the sensor power mode
func (b *BME280) SetPowerMode(mode byte) error {
	if !IsValidPowerMode(mode) {
		return fmt.Errorf("invalid power mode: 0x%02X", mode)
	}

	b.Lock()
	defer b.Unlock()
	return b.setPowerMode(mode)
}

func (b *BME280) setPowerMode(mode byte) error {
	// Read current ctrl_meas register
	buf := make([]byte, 1)
	if err := b.Device.ReadReg(BME280_REG_CTRL_MEAS, buf); err != nil {
		return err
	}

	// Update mode bits
	regValue := (buf[0] & ^BME280_SENSOR_MODE_MSK) | (mode & BME280_SENSOR_MODE_MSK)

	// Write back
	if err := b.Device.WriteReg(BME280_REG_CTRL_MEAS, []byte{regValue}); err != nil {
		return err
	}

	return nil
}

// GetPowerMode returns the current power mode
func (b *BME280) GetPowerMode() (byte, error) {
	b.Lock()
	defer b.Unlock()

	buf := make([]byte, 1)
	if err := b.Device.ReadReg(BME280_REG_CTRL_MEAS, buf); err != nil {
		return 0, err
	}

	return buf[0] & BME280_SENSOR_MODE_MSK, nil
}

// SetOversamplingSettings configures oversampling for pressure, temperature, and humidity
func (b *BME280) SetOversamplingSettings(pressure, temperature, humidity byte) error {
	if !IsValidOversamplingMode(pressure) || !IsValidOversamplingMode(temperature) || !IsValidOversamplingMode(humidity) {
		return errors.New("invalid oversampling settings")
	}

	b.Lock()
	defer b.Unlock()

	b.Settings.OversamplingP = pressure
	b.Settings.OversamplingT = temperature
	b.Settings.OversamplingH = humidity

	return b.applySettings()
}

// SetFilterSettings configures the IIR filter coefficient
func (b *BME280) SetFilterSettings(filterCoeff byte) error {
	if !IsValidFilterCoeff(filterCoeff) {
		return fmt.Errorf("invalid filter coefficient: 0x%02X", filterCoeff)
	}

	b.Lock()
	defer b.Unlock()

	b.Settings.Filter = filterCoeff
	return b.applyFilterSettings()
}

func (b *BME280) applyFilterSettings() error {
	// Read current config register
	buf := make([]byte, 1)
	if err := b.Device.ReadReg(BME280_REG_CONFIG, buf); err != nil {
		return err
	}

	// Update filter bits
	regValue := (buf[0] & ^BME280_FILTER_MSK) | ((b.Settings.Filter << BME280_FILTER_POS) & BME280_FILTER_MSK)

	// Write back
	return b.Device.WriteReg(BME280_REG_CONFIG, []byte{regValue})
}

// SetStandbyTime configures the standby time for normal mode
func (b *BME280) SetStandbyTime(standbyTime byte) error {
	if !IsValidStandbyTime(standbyTime) {
		return fmt.Errorf("invalid standby time: 0x%02X", standbyTime)
	}

	b.Lock()
	defer b.Unlock()

	b.Settings.StandbyTime = standbyTime
	return b.applyStandbySettings()
}

func (b *BME280) applyStandbySettings() error {
	// Read current config register
	buf := make([]byte, 1)
	if err := b.Device.ReadReg(BME280_REG_CONFIG, buf); err != nil {
		return err
	}

	// Update standby bits
	regValue := (buf[0] & ^BME280_STANDBY_MSK) | ((b.Settings.StandbyTime << BME280_STANDBY_POS) & BME280_STANDBY_MSK)

	// Write back
	return b.Device.WriteReg(BME280_REG_CONFIG, []byte{regValue})
}

// ApplySettings applies all current settings to the sensor
func (b *BME280) ApplySettings() error {
	b.Lock()
	defer b.Unlock()
	return b.applySettings()
}

func (b *BME280) applySettings() error {
	// Configure humidity oversampling first (must be done before ctrl_meas)
	if err := b.Device.WriteReg(BME280_REG_CTRL_HUM, []byte{b.Settings.OversamplingH}); err != nil {
		return err
	}

	// Configure pressure and temperature oversampling
	ctrlMeasValue := (b.Settings.OversamplingT << BME280_CTRL_TEMP_POS) |
		(b.Settings.OversamplingP << BME280_CTRL_PRESS_POS) |
		BME280_POWERMODE_SLEEP // Start in sleep mode

	if err := b.Device.WriteReg(BME280_REG_CTRL_MEAS, []byte{ctrlMeasValue}); err != nil {
		return err
	}

	// Configure filter and standby time
	configValue := (b.Settings.StandbyTime << BME280_STANDBY_POS) |
		(b.Settings.Filter << BME280_FILTER_POS)

	return b.Device.WriteReg(BME280_REG_CONFIG, []byte{configValue})
}

// ReadCalibrationData reads the factory calibration coefficients
func (b *BME280) ReadCalibrationData() error {
	b.Lock()
	defer b.Unlock()
	return b.readCalibrationData()
}

func (b *BME280) readCalibrationData() error {
	// Read temperature and pressure calibration data (0x88-0xA1)
	tempPressCalib := make([]byte, BME280_LEN_TEMP_PRESS_CALIB_DATA)
	if err := b.Device.ReadReg(BME280_REG_TEMP_PRESS_CALIB_DATA, tempPressCalib); err != nil {
		return err
	}

	// Parse temperature calibration coefficients
	b.CalibrationData.DigT1 = uint16(tempPressCalib[1])<<8 | uint16(tempPressCalib[0])
	b.CalibrationData.DigT2 = int16(uint16(tempPressCalib[3])<<8 | uint16(tempPressCalib[2]))
	b.CalibrationData.DigT3 = int16(uint16(tempPressCalib[5])<<8 | uint16(tempPressCalib[4]))

	// Parse pressure calibration coefficients
	b.CalibrationData.DigP1 = uint16(tempPressCalib[7])<<8 | uint16(tempPressCalib[6])
	b.CalibrationData.DigP2 = int16(uint16(tempPressCalib[9])<<8 | uint16(tempPressCalib[8]))
	b.CalibrationData.DigP3 = int16(uint16(tempPressCalib[11])<<8 | uint16(tempPressCalib[10]))
	b.CalibrationData.DigP4 = int16(uint16(tempPressCalib[13])<<8 | uint16(tempPressCalib[12]))
	b.CalibrationData.DigP5 = int16(uint16(tempPressCalib[15])<<8 | uint16(tempPressCalib[14]))
	b.CalibrationData.DigP6 = int16(uint16(tempPressCalib[17])<<8 | uint16(tempPressCalib[16]))
	b.CalibrationData.DigP7 = int16(uint16(tempPressCalib[19])<<8 | uint16(tempPressCalib[18]))
	b.CalibrationData.DigP8 = int16(uint16(tempPressCalib[21])<<8 | uint16(tempPressCalib[20]))
	b.CalibrationData.DigP9 = int16(uint16(tempPressCalib[23])<<8 | uint16(tempPressCalib[22]))

	// Read humidity calibration data
	// First part: H1 coefficient at 0xA1
	h1Buf := make([]byte, 1)
	if err := b.Device.ReadReg(0xA1, h1Buf); err != nil {
		return err
	}
	b.CalibrationData.DigH1 = h1Buf[0]

	// Second part: H2-H6 coefficients at 0xE1-0xE7
	humidCalib := make([]byte, BME280_LEN_HUMIDITY_CALIB_DATA)
	if err := b.Device.ReadReg(BME280_REG_HUMIDITY_CALIB_DATA, humidCalib); err != nil {
		return err
	}

	b.CalibrationData.DigH2 = int16(uint16(humidCalib[1])<<8 | uint16(humidCalib[0]))
	b.CalibrationData.DigH3 = humidCalib[2]
	b.CalibrationData.DigH4 = int16(int16(humidCalib[3])<<4 | int16(humidCalib[4]&0x0F))
	b.CalibrationData.DigH5 = int16(int16(humidCalib[5])<<4 | int16(humidCalib[4]>>4))
	b.CalibrationData.DigH6 = int8(humidCalib[6])

	l.Debug("BME280 calibration data read successfully")
	return nil
}

// GetCalibrationData returns the current calibration coefficients
func (b *BME280) GetCalibrationData() CalibrationData {
	b.Lock()
	defer b.Unlock()
	return b.CalibrationData
}

// ReadTemperature reads and returns compensated temperature in Celsius
func (b *BME280) ReadTemperature() (float32, error) {
	data, err := b.ReadAll()
	if err != nil {
		return 0, err
	}
	return data.Temperature, nil
}

// ReadHumidity reads and returns compensated humidity in %RH
func (b *BME280) ReadHumidity() (float32, error) {
	data, err := b.ReadAll()
	if err != nil {
		return 0, err
	}
	return data.Humidity, nil
}

// ReadPressure reads and returns compensated pressure in Pa
func (b *BME280) ReadPressure() (float32, error) {
	data, err := b.ReadAll()
	if err != nil {
		return 0, err
	}
	return float32(data.Pressure), nil
}

// ReadAll reads all sensor data (temperature, pressure, humidity) in one operation
func (b *BME280) ReadAll() (*SensorData, error) {
	b.Lock()
	defer b.Unlock()

	// Set to forced mode to trigger measurement
	if err := b.setPowerMode(BME280_POWERMODE_FORCED); err != nil {
		return nil, fmt.Errorf("failed to set forced mode: %w", err)
	}

	// Wait for measurement to complete
	measurementTime := b.calculateMeasurementTime()
	time.Sleep(time.Duration(measurementTime) * time.Microsecond)

	// Wait for measurement done bit
	for i := 0; i < 10; i++ {
		status, err := b.getStatus()
		if err != nil {
			return nil, err
		}
		if status&BME280_STATUS_MEAS_DONE != 0 {
			break
		}
		time.Sleep(1 * time.Millisecond)
	}

	// Read raw sensor data
	rawData, err := b.readRawData()
	if err != nil {
		return nil, fmt.Errorf("failed to read raw data: %w", err)
	}

	// Compensate the data
	return b.compensateData(rawData), nil
}

// ReadRawData reads uncompensated sensor data
func (b *BME280) ReadRawData() (*UncompensatedData, error) {
	b.Lock()
	defer b.Unlock()
	return b.readRawData()
}

func (b *BME280) readRawData() (*UncompensatedData, error) {
	// Read 8 bytes starting from pressure data register
	dataBytes := make([]byte, BME280_LEN_P_T_H_DATA)
	if err := b.Device.ReadReg(BME280_REG_DATA, dataBytes); err != nil {
		return nil, err
	}

	// Parse pressure (20-bit)
	pressure := uint32(dataBytes[0])<<12 | uint32(dataBytes[1])<<4 | uint32(dataBytes[2])>>4

	// Parse temperature (20-bit)
	temperature := uint32(dataBytes[3])<<12 | uint32(dataBytes[4])<<4 | uint32(dataBytes[5])>>4

	// Parse humidity (16-bit)
	humidity := uint32(dataBytes[6])<<8 | uint32(dataBytes[7])

	return &UncompensatedData{
		Temperature: temperature,
		Pressure:    pressure,
		Humidity:    humidity,
	}, nil
}

// compensateData applies calibration compensation to raw sensor data
func (b *BME280) compensateData(rawData *UncompensatedData) *SensorData {
	// Compensate temperature first (needed for pressure and humidity compensation)
	tempFloat := b.compensateTemperature(rawData.Temperature)

	// Compensate pressure
	pressureInt := b.compensatePressure(rawData.Pressure)

	// Compensate humidity
	humidityFloat := b.compensateHumidity(rawData.Humidity)

	return &SensorData{
		Temperature: tempFloat,
		Pressure:    pressureInt,
		Humidity:    humidityFloat,
	}
}

// compensateTemperature compensates temperature and updates t_fine
func (b *BME280) compensateTemperature(adcT uint32) float32 {
	var1 := float64(int32(adcT)>>3) - (float64(b.CalibrationData.DigT1) * 2.0)
	var1 = var1 * float64(b.CalibrationData.DigT2) / 2048.0

	var2 := float64(int32(adcT)>>4) - float64(b.CalibrationData.DigT1)
	var2 = var2 * var2 / 4096.0 * float64(b.CalibrationData.DigT3) / 16384.0

	// Store t_fine for pressure and humidity compensation
	b.CalibrationData.TFine = int32(var1 + var2)

	temperature := (var1 + var2) / 5120.0

	// Clamp to valid range
	if temperature < float64(BME280_TEMP_MIN) {
		temperature = float64(BME280_TEMP_MIN)
	} else if temperature > float64(BME280_TEMP_MAX) {
		temperature = float64(BME280_TEMP_MAX)
	}

	return float32(temperature)
}

// compensatePressure compensates pressure using t_fine from temperature compensation
func (b *BME280) compensatePressure(adcP uint32) uint32 {
	var1 := int64(b.CalibrationData.TFine) - 128000
	var2 := var1 * var1 * int64(b.CalibrationData.DigP6)
	var2 = var2 + ((var1 * int64(b.CalibrationData.DigP5)) << 17)
	var2 = var2 + (int64(b.CalibrationData.DigP4) << 35)
	var1 = ((var1 * var1 * int64(b.CalibrationData.DigP3)) >> 8) + ((var1 * int64(b.CalibrationData.DigP2)) << 12)
	var1 = (((int64(1) << 47) + var1) * int64(b.CalibrationData.DigP1)) >> 33

	if var1 == 0 {
		return 0 // Avoid division by zero
	}

	p := int64(1048576 - int64(adcP))
	p = (((p << 31) - var2) * 3125) / var1
	var1 = (int64(b.CalibrationData.DigP9) * (p >> 13) * (p >> 13)) >> 25
	var2 = (int64(b.CalibrationData.DigP8) * p) >> 19
	p = ((p + var1 + var2) >> 8) + (int64(b.CalibrationData.DigP7) << 4)

	pressure := uint32(p >> 8)

	// Clamp to valid range
	if pressure < BME280_PRESSURE_MIN {
		pressure = BME280_PRESSURE_MIN
	} else if pressure > BME280_PRESSURE_MAX {
		pressure = BME280_PRESSURE_MAX
	}

	return pressure
}

// compensateHumidity compensates humidity using t_fine from temperature compensation
func (b *BME280) compensateHumidity(adcH uint32) float32 {
	var1 := float64(b.CalibrationData.TFine) - 76800.0
	var2 := float64(b.CalibrationData.DigH4)*64.0 + (float64(b.CalibrationData.DigH5)/16384.0)*var1
	var3 := float64(adcH) - var2
	var4 := float64(b.CalibrationData.DigH2) / 65536.0
	var5 := 1.0 + (float64(b.CalibrationData.DigH3)/67108864.0)*var1
	var6 := 1.0 + (float64(b.CalibrationData.DigH6)/67108864.0)*var1*var5
	humidity := var3 * var4 * (var5 * var6)

	// Apply final compensation formula
	humidity = humidity * (1.0 - float64(b.CalibrationData.DigH1)*humidity/524288.0)

	// Clamp to valid range
	if humidity < float64(BME280_HUMIDITY_MIN) {
		humidity = float64(BME280_HUMIDITY_MIN)
	} else if humidity > float64(BME280_HUMIDITY_MAX) {
		humidity = float64(BME280_HUMIDITY_MAX)
	}

	return float32(humidity)
}

// CalculateAltitude calculates altitude in meters based on pressure reading
func (b *BME280) CalculateAltitude(seaLevelPressure float32) (float32, error) {
	pressure, err := b.ReadPressure()
	if err != nil {
		return 0, err
	}

	if seaLevelPressure == 0 {
		seaLevelPressure = BME280_SEA_LEVEL_PRESSURE * 100 // Convert hPa to Pa
	}

	// Barometric formula: h = 44330 * (1 - (P/P0)^0.1903)
	altitude := 44330.0 * (1.0 - math.Pow(float64(pressure)/float64(seaLevelPressure), 0.1903))

	return float32(altitude), nil
}

// calculateMeasurementTime calculates the measurement time based on current settings
func (b *BME280) calculateMeasurementTime() uint16 {
	// Base measurement time
	measureTime := BME280_MEAS_OFFSET

	// Add time for temperature measurement
	if b.Settings.OversamplingT != BME280_NO_OVERSAMPLING {
		measureTime += BME280_MEAS_DUR + uint16(b.Settings.OversamplingT)*BME280_MEAS_DUR
	}

	// Add time for pressure measurement
	if b.Settings.OversamplingP != BME280_NO_OVERSAMPLING {
		measureTime += BME280_MEAS_DUR + uint16(b.Settings.OversamplingP)*BME280_MEAS_DUR
	}

	// Add time for humidity measurement
	if b.Settings.OversamplingH != BME280_NO_OVERSAMPLING {
		measureTime += BME280_MEAS_DUR + uint16(b.Settings.OversamplingH)*BME280_MEAS_DUR
	}

	return measureTime
}

// GetMeasurementTime returns the calculated measurement time for current settings
func (b *BME280) GetMeasurementTime() uint16 {
	b.Lock()
	defer b.Unlock()
	return b.calculateMeasurementTime()
}

// IsEnabled returns whether the sensor is enabled
func (b *BME280) IsEnabled() bool {
	b.Lock()
	defer b.Unlock()
	return b.Enabled
}

// SetEnabled enables or disables the sensor
func (b *BME280) SetEnabled(enabled bool) error {
	b.Lock()
	defer b.Unlock()

	if enabled && !b.Enabled {
		// Enable sensor
		if err := b.setPowerMode(BME280_POWERMODE_NORMAL); err != nil {
			return err
		}
		b.Enabled = true
		l.Info("BME280 sensor enabled")
	} else if !enabled && b.Enabled {
		// Disable sensor
		if err := b.setPowerMode(BME280_POWERMODE_SLEEP); err != nil {
			return err
		}
		b.Enabled = false
		l.Info("BME280 sensor disabled")
	}

	return nil
}

// GetSettings returns the current sensor settings
func (b *BME280) GetSettings() SensorSettings {
	b.Lock()
	defer b.Unlock()
	return b.Settings
}

// SetSettings applies new sensor settings
func (b *BME280) SetSettings(settings SensorSettings) error {
	if !IsValidOversamplingMode(settings.OversamplingP) ||
		!IsValidOversamplingMode(settings.OversamplingT) ||
		!IsValidOversamplingMode(settings.OversamplingH) ||
		!IsValidFilterCoeff(settings.Filter) ||
		!IsValidStandbyTime(settings.StandbyTime) {
		return errors.New("invalid sensor settings")
	}

	b.Lock()
	defer b.Unlock()

	b.Settings = settings
	return b.applySettings()
}

// ReadCompensatedData reads and returns fully compensated sensor data
func (b *BME280) ReadCompensatedData() (*SensorData, error) {
	return b.ReadAll()
}
