package pitooth

import (
	"testing"
	"time"
)

func Test_NewBluetoothManager(t *testing.T) {
	_, err := NewBluetoothManager()
	if err != nil {
		t.Fatalf("Failed to create Bluetooth Manager: %v", err)
	}
}

func Test_Bluetooth(t *testing.T) {
	btm, err := NewBluetoothManager()
	if err != nil {
		t.Fatalf("Failed to create Bluetooth Manager: %v", err)
	}
	l.Debugln("PiTooth: Starting Bluetooth Test...")
	address, err := btm.GetAddress()
	if err != nil {
		t.Fatalf("Failed to get device address: %v", err)
	}
	l.Printf("Device address: %s", address)

	err = btm.SetAlias("SunlightMeter")
	if err != nil {
		t.Fatalf("Failed to set name: %v", err)
	}

	// Make the device discoverable
	l.Debugln("PiTooth: Setting Discoverable...")
	err = btm.SetDiscoverable(true)
	if err != nil {
		t.Fatalf("Failed to make device discoverable: %v", err)
	}

	l.Debugln("PiTooth: Setting Pairable...")
	err = btm.SetPairable(true)
	if err != nil {
		t.Fatalf("Failed to make device pairable: %v", err)
	}

	// Start the discovery
	l.Debugln("PiTooth: Starting Discovery...")
	err = btm.StartDiscovery()
	if err != nil {
		t.Fatalf("Failed to start bluetooth discovery: %v", err)
	}

	// Map to remember devices we've seen in the last 15 seconds.
	// Sometimes we dont see them all in a single scan
	for {
		// Get discovered devices
		l.Debugln("PiTooth: After Discovery - ")
		devices, err := btm.GetDevices()
		if err != nil {
			t.Fatalf("Failed to get bluetooth devices: %v", err)
		}

		// Log them
		l.Debugln("PiTooth: After GetDevices - ")
		for _, device := range devices {
			l.Printf("PiTooth: Discovered bluetooth device: %s : %v", device.Properties.Alias, device.Properties.Address)
			l.Printf("PiTooth: Properties: %v", device.Properties)
		}
		time.Sleep(15 * time.Second)
	}
}
