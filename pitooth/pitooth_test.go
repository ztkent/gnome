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
	btm.Pairing("SunlightMeterServer")
	// Create the manager. 
	// Allow connections to the server for a while, return who we are paired with.
	// It better be the client
}

func Test_Client(t *testing.T) {
	btm, err := NewBluetoothManager()
	if err != nil {
		t.Fatalf("Failed to create Bluetooth Manager: %v", err)
	}
	btm.Pairing("SunlightMeterClient")
}