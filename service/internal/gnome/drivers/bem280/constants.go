package bem280

// BME280 Sensor Constants
// Based on Bosch Sensortec BME280 datasheet and official API v3.5.1
// https://github.com/BoschSensortec/BME280_SensorAPI

const (
	// I2C Addresses
	BME280_I2C_ADDR_PRIM uint16 = 0x76 ///< Primary I2C address (SDO pulled to GND)
	BME280_I2C_ADDR_SEC  uint16 = 0x77 ///< Secondary I2C address (SDO pulled to VDDIO)
	BME280_ADDR          uint16 = 0x77 ///< Default I2C address (for backward compatibility)

	// Chip Identification
	BME280_CHIP_ID byte = 0x60 ///< BME280 chip identifier

	// Register Addresses
	BME280_REG_CHIP_ID               byte = 0xD0 ///< Chip ID register
	BME280_REG_RESET                 byte = 0xE0 ///< Reset register
	BME280_REG_TEMP_PRESS_CALIB_DATA byte = 0x88 ///< Temperature and pressure calibration data start
	BME280_REG_HUMIDITY_CALIB_DATA   byte = 0xE1 ///< Humidity calibration data start
	BME280_REG_CTRL_HUM              byte = 0xF2 ///< Humidity control register
	BME280_REG_STATUS                byte = 0xF3 ///< Status register
	BME280_REG_CTRL_MEAS             byte = 0xF4 ///< Measurement control register
	BME280_REG_CONFIG                byte = 0xF5 ///< Configuration register
	BME280_REG_DATA                  byte = 0xF7 ///< Pressure and temperature data start

	// Data lengths
	BME280_LEN_TEMP_PRESS_CALIB_DATA byte = 26 ///< Temperature and pressure calibration data length
	BME280_LEN_HUMIDITY_CALIB_DATA   byte = 7  ///< Humidity calibration data length
	BME280_LEN_P_T_H_DATA            byte = 8  ///< Pressure, temperature, humidity data length

	// Power Modes
	BME280_POWERMODE_SLEEP  byte = 0x00 ///< Sleep mode
	BME280_POWERMODE_FORCED byte = 0x01 ///< Forced mode
	BME280_POWERMODE_NORMAL byte = 0x03 ///< Normal mode

	// Oversampling Settings
	BME280_NO_OVERSAMPLING  byte = 0x00 ///< No oversampling
	BME280_OVERSAMPLING_1X  byte = 0x01 ///< 1x oversampling
	BME280_OVERSAMPLING_2X  byte = 0x02 ///< 2x oversampling
	BME280_OVERSAMPLING_4X  byte = 0x03 ///< 4x oversampling
	BME280_OVERSAMPLING_8X  byte = 0x04 ///< 8x oversampling
	BME280_OVERSAMPLING_16X byte = 0x05 ///< 16x oversampling
	BME280_OVERSAMPLING_MAX byte = 16   ///< Maximum oversampling value

	// IIR Filter Coefficients
	BME280_FILTER_COEFF_OFF byte = 0x00 ///< Filter off
	BME280_FILTER_COEFF_2   byte = 0x01 ///< Filter coefficient 2
	BME280_FILTER_COEFF_4   byte = 0x02 ///< Filter coefficient 4
	BME280_FILTER_COEFF_8   byte = 0x03 ///< Filter coefficient 8
	BME280_FILTER_COEFF_16  byte = 0x04 ///< Filter coefficient 16

	// Standby Time Settings (for normal mode)
	BME280_STANDBY_TIME_0_5_MS  byte = 0x00 ///< Standby time 0.5ms
	BME280_STANDBY_TIME_62_5_MS byte = 0x01 ///< Standby time 62.5ms
	BME280_STANDBY_TIME_125_MS  byte = 0x02 ///< Standby time 125ms
	BME280_STANDBY_TIME_250_MS  byte = 0x03 ///< Standby time 250ms
	BME280_STANDBY_TIME_500_MS  byte = 0x04 ///< Standby time 500ms
	BME280_STANDBY_TIME_1000_MS byte = 0x05 ///< Standby time 1000ms
	BME280_STANDBY_TIME_10_MS   byte = 0x06 ///< Standby time 10ms
	BME280_STANDBY_TIME_20_MS   byte = 0x07 ///< Standby time 20ms

	// Soft Reset Command
	BME280_SOFT_RESET_COMMAND byte = 0xB6 ///< Soft reset command

	// Status Register Bits
	BME280_STATUS_IM_UPDATE byte = 0x01 ///< NVM data copying in progress
	BME280_STATUS_MEAS_DONE byte = 0x08 ///< Measurement completed

	// Sensor Component Selection
	BME280_PRESS byte = 0x01 ///< Pressure sensor
	BME280_TEMP  byte = 0x02 ///< Temperature sensor
	BME280_HUM   byte = 0x04 ///< Humidity sensor
	BME280_ALL   byte = 0x07 ///< All sensors

	// Settings Selection Macros
	BME280_SEL_OSR_PRESS    byte = 0x01 ///< Pressure oversampling selection
	BME280_SEL_OSR_TEMP     byte = 0x02 ///< Temperature oversampling selection
	BME280_SEL_OSR_HUM      byte = 0x04 ///< Humidity oversampling selection
	BME280_SEL_FILTER       byte = 0x08 ///< Filter selection
	BME280_SEL_STANDBY      byte = 0x10 ///< Standby selection
	BME280_SEL_ALL_SETTINGS byte = 0x1F ///< All settings selection

	// Register Bit Masks and Positions
	// Humidity Control Register (0xF2)
	BME280_CTRL_HUM_MSK byte = 0x07 ///< Humidity oversampling mask
	BME280_CTRL_HUM_POS byte = 0x00 ///< Humidity oversampling position

	// Measurement Control Register (0xF4)
	BME280_CTRL_PRESS_MSK  byte = 0x1C ///< Pressure oversampling mask
	BME280_CTRL_PRESS_POS  byte = 0x02 ///< Pressure oversampling position
	BME280_CTRL_TEMP_MSK   byte = 0xE0 ///< Temperature oversampling mask
	BME280_CTRL_TEMP_POS   byte = 0x05 ///< Temperature oversampling position
	BME280_SENSOR_MODE_MSK byte = 0x03 ///< Sensor mode mask
	BME280_SENSOR_MODE_POS byte = 0x00 ///< Sensor mode position

	// Configuration Register (0xF5)
	BME280_FILTER_MSK  byte = 0x1C ///< Filter mask
	BME280_FILTER_POS  byte = 0x02 ///< Filter position
	BME280_STANDBY_MSK byte = 0xE0 ///< Standby time mask
	BME280_STANDBY_POS byte = 0x05 ///< Standby time position

	// Bit Shift Values
	BME280_12_BIT_SHIFT byte = 12 ///< 12-bit shift
	BME280_8_BIT_SHIFT  byte = 8  ///< 8-bit shift
	BME280_4_BIT_SHIFT  byte = 4  ///< 4-bit shift

	// Measurement Timing Constants
	BME280_MEAS_OFFSET          uint16 = 1250 ///< Measurement offset in microseconds
	BME280_MEAS_DUR             uint16 = 2300 ///< Measurement duration in microseconds
	BME280_PRES_HUM_MEAS_OFFSET uint16 = 575  ///< Pressure/humidity measurement offset in microseconds
	BME280_MEAS_SCALING_FACTOR  uint16 = 1000 ///< Measurement scaling factor
	BME280_STARTUP_DELAY        uint16 = 2000 ///< Startup delay in microseconds

	// Maximum Transfer Length
	BME280_MAX_LEN byte = 10 ///< Maximum transfer length

	// API Return Codes
	BME280_OK                int8 = 0  ///< Success
	BME280_E_NULL_PTR        int8 = -1 ///< Null pointer error
	BME280_E_COMM_FAIL       int8 = -2 ///< Communication failure
	BME280_E_INVALID_LEN     int8 = -3 ///< Invalid length
	BME280_E_DEV_NOT_FOUND   int8 = -4 ///< Device not found
	BME280_E_SLEEP_MODE_FAIL int8 = -5 ///< Sleep mode failure
	BME280_E_NVM_COPY_FAILED int8 = -6 ///< NVM copy failed

	// API Warning Codes
	BME280_W_INVALID_OSR_MACRO int8 = 1 ///< Invalid oversampling macro

	// Sensor Ranges and Limits
	BME280_TEMP_MIN     float32 = -40.0  ///< Minimum temperature (°C)
	BME280_TEMP_MAX     float32 = 85.0   ///< Maximum temperature (°C)
	BME280_PRESSURE_MIN uint32  = 30000  ///< Minimum pressure (Pa)
	BME280_PRESSURE_MAX uint32  = 110000 ///< Maximum pressure (Pa)
	BME280_HUMIDITY_MIN float32 = 0.0    ///< Minimum humidity (%)
	BME280_HUMIDITY_MAX float32 = 100.0  ///< Maximum humidity (%)

	// Sea Level Pressure (for altitude calculation)
	BME280_SEA_LEVEL_PRESSURE float32 = 1013.25 ///< Standard sea level pressure (hPa)
)

// CalibrationData represents the BME280 calibration coefficients
// These are factory-calibrated values stored in the sensor's NVM
type CalibrationData struct {
	// Temperature calibration coefficients
	DigT1 uint16 ///< dig_T1 coefficient
	DigT2 int16  ///< dig_T2 coefficient
	DigT3 int16  ///< dig_T3 coefficient

	// Pressure calibration coefficients
	DigP1 uint16 ///< dig_P1 coefficient
	DigP2 int16  ///< dig_P2 coefficient
	DigP3 int16  ///< dig_P3 coefficient
	DigP4 int16  ///< dig_P4 coefficient
	DigP5 int16  ///< dig_P5 coefficient
	DigP6 int16  ///< dig_P6 coefficient
	DigP7 int16  ///< dig_P7 coefficient
	DigP8 int16  ///< dig_P8 coefficient
	DigP9 int16  ///< dig_P9 coefficient

	// Humidity calibration coefficients
	DigH1 uint8 ///< dig_H1 coefficient
	DigH2 int16 ///< dig_H2 coefficient
	DigH3 uint8 ///< dig_H3 coefficient
	DigH4 int16 ///< dig_H4 coefficient
	DigH5 int16 ///< dig_H5 coefficient
	DigH6 int8  ///< dig_H6 coefficient

	// Fine temperature value for pressure and humidity calculations
	TFine int32 ///< t_fine variable for compensation calculations
}

// SensorData represents compensated sensor readings
type SensorData struct {
	Temperature float32 ///< Compensated temperature in °C
	Pressure    uint32  ///< Compensated pressure in Pa
	Humidity    float32 ///< Compensated humidity in %RH
}

// UncompensatedData represents raw sensor readings
type UncompensatedData struct {
	Temperature uint32 ///< Raw temperature ADC value
	Pressure    uint32 ///< Raw pressure ADC value
	Humidity    uint32 ///< Raw humidity ADC value
}

// SensorSettings represents BME280 configuration settings
type SensorSettings struct {
	OversamplingP byte ///< Pressure oversampling setting
	OversamplingT byte ///< Temperature oversampling setting
	OversamplingH byte ///< Humidity oversampling setting
	Filter        byte ///< IIR filter coefficient
	StandbyTime   byte ///< Standby time for normal mode
}

// Helper functions for setting validation

// IsValidOversamplingMode checks if the oversampling mode is valid
func IsValidOversamplingMode(mode byte) bool {
	return mode <= BME280_OVERSAMPLING_16X
}

// IsValidFilterCoeff checks if the filter coefficient is valid
func IsValidFilterCoeff(coeff byte) bool {
	return coeff <= BME280_FILTER_COEFF_16
}

// IsValidStandbyTime checks if the standby time is valid
func IsValidStandbyTime(time byte) bool {
	return time <= BME280_STANDBY_TIME_20_MS
}

// IsValidPowerMode checks if the power mode is valid
func IsValidPowerMode(mode byte) bool {
	return mode == BME280_POWERMODE_SLEEP ||
		mode == BME280_POWERMODE_FORCED ||
		mode == BME280_POWERMODE_NORMAL
}
