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
