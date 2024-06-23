package pitooth

import (
	"fmt"

	"github.com/godbus/dbus/v5"
	"github.com/muka/go-bluetooth/bluez/profile/agent"
)

/* 
	An Agent is how bluetooth controls the pairing process. 
	It is responsible for displaying the passkey, pincode, etc. to the user and handling the user's response.
	Implementing this custom agent allows us to tap into the pairing process.
	In this case, the goal is to allow trusted pairing without additional user interaction.
*/

type PiToothAgent struct {
	*agent.SimpleAgent
}

// This is called when a client that is already paired to the device tries to connect to it.
func (a *PiToothAgent) AuthorizeService(device dbus.ObjectPath, uuid string) *dbus.Error {
	fmt.Println("AuthorizeService called")
	return a.SimpleAgent.AuthorizeService(device, uuid)
}

func (a *PiToothAgent) Cancel() *dbus.Error {
	fmt.Println("Cancel called")
	return a.SimpleAgent.Cancel()
}

func (a *PiToothAgent) DisplayPasskey(device dbus.ObjectPath, passkey uint32, entered uint16) *dbus.Error {
	fmt.Println("DisplayPasskey called")
	return a.SimpleAgent.DisplayPasskey(device, passkey, entered)
}

func (a *PiToothAgent) DisplayPinCode(device dbus.ObjectPath, pincode string) *dbus.Error {
	fmt.Println("DisplayPinCode called")
	return a.SimpleAgent.DisplayPinCode(device, pincode)
}

func (a *PiToothAgent) Interface() string {
	fmt.Println("Interface called")
	return a.SimpleAgent.Interface()
}

func (a *PiToothAgent) PassCode() string {
	fmt.Println("PassCode called")
	return a.SimpleAgent.PassCode()
}

func (a *PiToothAgent) PassKey() uint32 {
	fmt.Println("PassKey called")
	return a.SimpleAgent.PassKey()
}

func (a *PiToothAgent) Path() dbus.ObjectPath {
	fmt.Println("Path called")
	return a.SimpleAgent.Path()
}

func (a *PiToothAgent) Release() *dbus.Error {
	fmt.Println("Release called")
	return a.SimpleAgent.Release()
}

func (a *PiToothAgent) RequestAuthorization(device dbus.ObjectPath) *dbus.Error {
	fmt.Println("RequestAuthorization called")
	return a.SimpleAgent.RequestAuthorization(device)
}

func (a *PiToothAgent) RequestConfirmation(path dbus.ObjectPath, passkey uint32) *dbus.Error {
	fmt.Println("RequestConfirmation called")
	return a.SimpleAgent.RequestConfirmation(path, passkey)
}

func (a *PiToothAgent) RequestPasskey(path dbus.ObjectPath) (uint32, *dbus.Error) {
	fmt.Println("RequestPasskey called")
	return a.SimpleAgent.RequestPasskey(path)
}

func (a *PiToothAgent) RequestPinCode(path dbus.ObjectPath) (string, *dbus.Error) {
	fmt.Println("RequestPinCode called")
	return a.SimpleAgent.RequestPinCode(path)
}

func (a *PiToothAgent) SetPassCode(pinCode string) {
	fmt.Println("SetPassCode called")
	a.SimpleAgent.SetPassCode(pinCode)
}

func (a *PiToothAgent) SetPassKey(passkey uint32) {
	fmt.Println("SetPassKey called")
	a.SimpleAgent.SetPassKey(passkey)
}
