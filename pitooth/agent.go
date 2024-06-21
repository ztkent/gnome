package pitooth

import (
	"fmt"

	"github.com/godbus/dbus/v5"
	"github.com/muka/go-bluetooth/bluez/profile/agent"
)

type PiToothAgent struct {
	*agent.SimpleAgent
}

func (a *PiToothAgent) RequestConfirmation(path dbus.ObjectPath, passkey uint32) *dbus.Error {
	fmt.Printf("Device %s paired with passkey %d\n", path, passkey)
	return nil
}
