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

type BluetoothManager struct {
	*adapter.Adapter1
	*agent.SimpleAgent
}

func NewBluetoothManager() (*BluetoothManager, error) {
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

	// Register a bt agent to handle pairing requests
	simpleAgent := agent.NewSimpleAgent()
	err = agent.ExposeAgent(defaultAdapter.Client().GetConnection(), simpleAgent, agent.CapNoInputNoOutput, true)
	if err != nil {
		return nil, fmt.Errorf("Failed to register agent: %v", err)
	}

	return &BluetoothManager{
		Adapter1:    defaultAdapter,
		SimpleAgent: simpleAgent,
	}, nil
}

func (btm *BluetoothManager) Pairing(deviceName string) error {
	l.Panicln("PiTooth: Starting Pairing...")
	l.Println("PiTooth: Setting Alias...", deviceName)
	err := btm.SetAlias(deviceName)
	if err != nil {
		return fmt.Errorf("Failed to set bluetooth alias: %v", err)
	}

	// Make the device discoverable
	l.Println("PiTooth: Setting Discoverable...")
	err = btm.SetDiscoverable(true)
	if err != nil {
		return fmt.Errorf("Failed to make device discoverable: %v", err)
	}

	l.Println("PiTooth: Setting Pairable...")
	err = btm.SetPairable(true)
	if err != nil {
		return fmt.Errorf("Failed to make device pairable: %v", err)
	}

	// Start the discovery
	l.Println("PiTooth: Starting Discovery...")
	err = btm.StartDiscovery()
	if err != nil {
		return fmt.Errorf("Failed to start bluetooth discovery: %v", err)
	}

	// Wait for the device to be discovered
	l.Println("PiTooth: Waiting for device to be discovered...")
	for {
		time.Sleep(15 * time.Second)
	}
}
