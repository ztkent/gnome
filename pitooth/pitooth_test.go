package pitooth

import (
	"testing"
)

func Test_NewBluetoothManager(t *testing.T) {
	btm, err := NewBluetoothManager("SunlightMeter")
	if err != nil || btm == nil {
		t.Fatalf("Failed to create Bluetooth Manager: %v", err)
	}
	btm.Close(true)
}

func Test_Server(t *testing.T) {
	btm, err := NewBluetoothManager("SunlightMeterServer")
	if err != nil {
		t.Fatalf("Failed to create Bluetooth Manager: %v", err)
	}
	defer btm.Close(true)

	connectedDevices, err := btm.AcceptConnections()
	if err != nil {
		t.Fatalf("Failed to accept connections: %v", err)
	}
	if len(connectedDevices) != 1 {
		t.Fatalf("Expected 1 connected device, got %d", len(connectedDevices))
	}
}
