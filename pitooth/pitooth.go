package pitooth

import (
	"fmt"
	"os"
	"runtime"
	"strings"
	"time"

	"github.com/muka/go-bluetooth/bluez/profile/adapter"
	"github.com/muka/go-bluetooth/bluez/profile/agent"
	"github.com/sirupsen/logrus"
)

var l = logrus.New()

func init() {
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
		l.SetLevel(logrus.InfoLevel)
	}
}

type BluetoothManager interface {
	Pairing(deviceName string) error
	GetNearbyDevices() error
	Close()
}

type bluetoothManager struct {
	adapter *adapter.Adapter1
	agent   *PiToothAgent
}

func NewBluetoothManager() (BluetoothManager, error) {
	// Only support Linux, this should be running on a Raspberry Pi
	if runtime.GOOS != "linux" {
		return nil, fmt.Errorf("Unsupported OS: %v", runtime.GOOS)
	} else {
		_, err := os.Stat("/proc/device-tree/model")
		if err != nil {
			return nil, fmt.Errorf("Not a Raspberry Pi, can't enable Bluetooth Discovery: %v", err)
		}
	}

	// Get the bt adapter to manage bluetooth devices
	defaultAdapter, err := adapter.GetDefaultAdapter()
	if err != nil {
		return nil, fmt.Errorf("Failed to get default adapter: %v", err)
	}

	// Expose a bt agent to handle pairing requests
	pitoothAgent := &PiToothAgent{
		SimpleAgent: agent.NewSimpleAgent(),
	}
	err = agent.ExposeAgent(defaultAdapter.Client().GetConnection(), pitoothAgent, agent.CapNoInputNoOutput, true)
	if err != nil {
		return nil, fmt.Errorf("Failed to register agent: %v", err)
	}

	return &bluetoothManager{
		adapter: defaultAdapter,
		agent:   pitoothAgent,
	}, nil
}

func (btm *bluetoothManager) Pairing(deviceName string) error {
	l.Debugln("PiTooth: Starting Pairing...")
	l.Debugln("PiTooth: Setting Alias...", deviceName)
	err := btm.adapter.SetAlias(deviceName)
	if err != nil {
		return fmt.Errorf("Failed to set bluetooth alias: %v", err)
	}

	// Make the device discoverable
	l.Debugln("PiTooth: Setting Discoverable...")
	err = btm.adapter.SetDiscoverable(true)
	if err != nil {
		return fmt.Errorf("Failed to make device discoverable: %v", err)
	}

	l.Debugln("PiTooth: Setting Pairable...")
	err = btm.adapter.SetPairable(true)
	if err != nil {
		return fmt.Errorf("Failed to make device pairable: %v", err)
	}

	// Start the discovery
	l.Debugln("PiTooth: Starting Discovery...")
	err = btm.adapter.StartDiscovery()
	if err != nil {
		return fmt.Errorf("Failed to start bluetooth discovery: %v", err)
	}

	// Wait for the device to be discovered
	l.Debugln("PiTooth: Waiting for device to be discovered...")
	for {
		time.Sleep(15 * time.Second)
	}
}

func (btm *bluetoothManager) GetNearbyDevices() error {
	// Map to remember devices we've seen in the last 15 seconds.
	// Sometimes we dont see them all in a single scan
	for {
		// Get discovered devices
		l.Debugln("PiTooth: After Discovery - ")
		devices, err := btm.adapter.GetDevices()
		if err != nil {
			return fmt.Errorf("Failed to get bluetooth devices: %v", err)
		}

		// Log them
		l.Debugln("PiTooth: After GetDevices - ")
		for _, device := range devices {
			l.Printf("PiTooth: Discovered bluetooth device: %s : %v", device.Properties.Alias, device.Properties.Address)
		}
		time.Sleep(15 * time.Second)
	}
}

func (btm *bluetoothManager) Close() {
	btm.adapter.StopDiscovery()
	btm.adapter.SetDiscoverable(false)
	btm.adapter.SetPairable(false)
	btm.agent.Cancel()
}
