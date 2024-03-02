package tsl2591

/*
 * tsl2591 - Package for interacting with TSL2591 lux sensors.
 *
 * Ported with changes:
 * https://github.com/mstahl/tsl2591/blob/master/tsl2591.go
 *
 */

import (
	"encoding/binary"
	"errors"
	"fmt"
	"math"
	"time"

	"golang.org/x/exp/io/i2c"
)

type Opts struct {
	Gain   byte
	Timing byte
}

type TSL2591 struct {
	enabled bool
	timing  byte
	gain    byte
	dev     *i2c.Device
}

// Connect to a TSL2591 via I2C protocol & set gain/timing
func NewTSL2591(opts *Opts) (*TSL2591, error) {
	// TODO: this path is no guarenteed
	device, err := i2c.Open(&i2c.Devfs{Dev: "/dev/i2c-2"}, int(TSL2591_ADDR))
	if err != nil {
		return nil, fmt.Errorf("Failed to open: %w", err)
	}
	tsl := &TSL2591{
		dev: device,
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
	tsl.Enable()

	// Delay for ADC to complete
	for d := byte(0); d < tsl.timing; d++ {
		time.Sleep(120 * time.Millisecond)
	}

	bytes := make([]byte, 4)

	err := tsl.dev.ReadReg(TSL2591_COMMAND_BIT|TSL2591_REGISTER_CHAN0_LOW, bytes)
	if err != nil {
		return 0, 0, err
	}

	channel0 := binary.LittleEndian.Uint16(bytes[0:])
	channel1 := binary.LittleEndian.Uint16(bytes[2:])

	tsl.Disable()
	return channel0, channel1, nil
}

func (tsl *TSL2591) CalculateLux(ch0, ch1 uint16) float64 {
	var (
		atime float64
		again float64

		cpl float64
		lux float64
	)

	// Return +Inf for overflow
	if ch0 == 0xFFFF || ch1 == 0xFFFF {
		return math.Inf(1)
	}

	switch tsl.timing {
	case TSL2591_INTEGRATIONTIME_100MS:
		atime = 100.0
	case TSL2591_INTEGRATIONTIME_200MS:
		atime = 200.0
	case TSL2591_INTEGRATIONTIME_300MS:
		atime = 300.0
	case TSL2591_INTEGRATIONTIME_400MS:
		atime = 400.0
	case TSL2591_INTEGRATIONTIME_500MS:
		atime = 500.0
	case TSL2591_INTEGRATIONTIME_600MS:
		atime = 600.0
	default:
		atime = 100.0
	}

	switch tsl.gain {
	case TSL2591_GAIN_LOW:
		again = 1.0
	case TSL2591_GAIN_MED:
		again = 25.0
	case TSL2591_GAIN_HIGH:
		again = 428.0
	case TSL2591_GAIN_MAX:
		again = 9876.0
	default:
		again = 1.0
	}

	cpl = (atime * again) / TSL2591_LUX_DF
	lux = (float64(ch0) - float64(ch1)) * (1.0 - (float64(ch1) / float64(ch0))) / cpl

	return lux
}
