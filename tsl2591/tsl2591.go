package tsl2591

/*
 * tsl2591 - Package for interacting with TSL2591 lux sensors.
 *
 * Ported with changes:
 * https://github.com/adafruit/Adafruit_TSL2591_Library
 * https://github.com/mstahl/tsl2591
 *
 */

import (
	"encoding/binary"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/Ztkent/sunlight-meter/internal/tools"
	"golang.org/x/exp/io/i2c"
)

type Opts struct {
	Gain    byte
	Timing  byte
	DevPath string
}

type TSL2591 struct {
	enabled bool
	timing  byte
	gain    byte
	dev     *i2c.Device
	*sync.Mutex
}

// Connect to a TSL2591 via I2C protocol & set gain/timing
func NewTSL2591(opts *Opts) (*TSL2591, error) {
	path := "/dev/i2c-1"
	if opts.DevPath != "" {
		path = opts.DevPath
	}
	device, err := i2c.Open(&i2c.Devfs{Dev: path}, int(TSL2591_ADDR))
	if err != nil {
		return nil, fmt.Errorf("Failed to open: %w", err)
	}
	tsl := &TSL2591{
		dev:   device,
		Mutex: &sync.Mutex{},
	}

	// Read the device ID from the TSL2591. It should be 0x50
	buf := make([]byte, 1)
	err = tsl.dev.ReadReg(TSL2591_COMMAND_BIT|TSL2591_REGISTER_DEVICE_ID, buf)
	if err != nil {
		return nil, fmt.Errorf("Failed to read ref: %w", err)
	}
	if buf[0] != 0x50 {
		return nil, errors.New("Can't find a TSL2591 on I2C bus /dev/i2c-1")
	}

	tsl.SetTiming(opts.Timing)
	tsl.SetGain(opts.Gain)

	tsl.Disable()
	return tsl, nil
}

func (tsl *TSL2591) Enable() error {
	tsl.Lock()
	defer tsl.Unlock()

	if tsl.enabled {
		return nil
	}
	var write []byte = []byte{
		TSL2591_ENABLE_POWERON | TSL2591_ENABLE_AEN | TSL2591_ENABLE_AIEN | TSL2591_ENABLE_NPIEN,
	}
	if err := tsl.dev.WriteReg(TSL2591_COMMAND_BIT|TSL2591_REGISTER_ENABLE, write); err != nil {
		return err
	}
	tsl.enabled = true
	return nil
}

func (tsl *TSL2591) Disable() error {
	tsl.Lock()
	defer tsl.Unlock()

	if !tsl.enabled {
		return nil
	}
	var write []byte = []byte{
		TSL2591_ENABLE_POWEROFF,
	}
	if err := tsl.dev.WriteReg(TSL2591_COMMAND_BIT|TSL2591_REGISTER_ENABLE, write); err != nil {
		return err
	}
	tsl.enabled = false
	return nil
}

func (tsl *TSL2591) SetGain(gain byte) error {
	tsl.Enable()
	write := []byte{
		tsl.timing | gain,
	}
	if err := tsl.dev.WriteReg(TSL2591_COMMAND_BIT|TSL2591_REGISTER_CONTROL, write); err != nil {
		return err
	}
	tsl.gain = gain
	tsl.Disable()
	return nil
}

func (tsl *TSL2591) SetTiming(timing byte) error {
	tsl.Enable()
	write := []byte{
		timing | tsl.gain,
	}
	err := tsl.dev.WriteReg(TSL2591_COMMAND_BIT|TSL2591_REGISTER_CONTROL, write)
	if err != nil {
		return err
	}
	tsl.timing = timing
	tsl.Disable()
	return nil
}

func (tsl *TSL2591) GetFullLuminosity() (uint16, uint16, error) {
	for d := byte(0); d < tsl.timing; d++ {
		time.Sleep(120 * time.Millisecond)
	}

	// Reading from TSL2591_REGISTER_CHAN0_LOW, and TSL2591_REGISTER_CHAN1_LOW
	// They are 2 bytes each, so we read 4 bytes in total
	bytes := make([]byte, 4)
	err := tsl.dev.ReadReg(TSL2591_COMMAND_BIT|TSL2591_REGISTER_CHAN0_LOW, bytes)
	if err != nil {
		fmt.Printf("Error reading from register: %v\n", err)
		return 0, 0, err
	}
	tools.DebugLog(fmt.Sprintf("Bytes read: %v\n", bytes))

	channel0 := binary.LittleEndian.Uint16(bytes[0:])
	channel1 := binary.LittleEndian.Uint16(bytes[2:])

	tools.DebugLog(fmt.Sprintf("Channel 0: %v, Channel 1: %v\n", channel0, channel1))
	return channel0, channel1, nil
}

func (tsl *TSL2591) CalculateLux(ch0, ch1 uint16) (float64, error) {
	// Return +Inf for overflow
	if ch0 == 0xFFFF || ch1 == 0xFFFF {
		return 0, fmt.Errorf("ch0/1 overflow")
	}

	var int_time float64
	switch tsl.timing {
	case TSL2591_INTEGRATIONTIME_100MS:
		int_time = 100.0
	case TSL2591_INTEGRATIONTIME_200MS:
		int_time = 200.0
	case TSL2591_INTEGRATIONTIME_300MS:
		int_time = 300.0
	case TSL2591_INTEGRATIONTIME_400MS:
		int_time = 400.0
	case TSL2591_INTEGRATIONTIME_500MS:
		int_time = 500.0
	case TSL2591_INTEGRATIONTIME_600MS:
		int_time = 600.0
	default:
		int_time = 100.0
	}

	var adj_gain float64
	switch tsl.gain {
	case TSL2591_GAIN_LOW:
		adj_gain = 1.0
	case TSL2591_GAIN_MED:
		adj_gain = 25.0
	case TSL2591_GAIN_HIGH:
		adj_gain = 428.0
	case TSL2591_GAIN_MAX:
		adj_gain = 9876.0
	default:
		adj_gain = 1.0
	}

	cpl := (int_time * adj_gain) / TSL2591_LUX_DF
	lux := (float64(ch0) - float64(ch1)) * (1.0 - (float64(ch1) / float64(ch0))) / cpl
	return lux, nil
}
