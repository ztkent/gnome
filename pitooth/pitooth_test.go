package pitooth

import (
	"testing"
)

func Test_NewBluetoothManager(t *testing.T) {
	_, err := NewBluetoothManager()
	if err != nil {
		t.Fatalf("Failed to create Bluetooth Manager: %v", err)
	}
}

func Test_Server(t *testing.T) {
	btm, err := NewBluetoothManager()
	if err != nil {
		t.Fatalf("Failed to create Bluetooth Manager: %v", err)
	}
	
	// Create the manager. 
	// Allow connections to the server for a while, return who we are paired with.
	// It better be the client
	connectedDevices, err := btm.AcceptConnections("SunlightMeterServer")
	if err != nil {
		t.Fatalf("Failed to accept connections: %v", err)
	}
	if len(connectedDevices) != 1 {
		t.Fatalf("Expected 1 connected device, got %d", len(connectedDevices))
	}
}

func Test_Client(t *testing.T) {
	btm, err := NewBluetoothManager()
	if err != nil {
		t.Fatalf("Failed to create Bluetooth Manager: %v", err)
	}
	btm.AcceptConnections("SunlightMeterClient")
}