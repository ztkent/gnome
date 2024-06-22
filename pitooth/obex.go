package pitooth

import (
	"fmt"
	"os/exec"
)

// https://github.com/muka/go-bluetooth/blob/master/examples/obex_push/obex_push.go
// Raspberry Pi as OBEX Server: Configure obexd to accept incoming connections and receive files.
// Mac as OBEX Client: Use macOS's built-in Bluetooth file transfer or third-party software to send files to the Raspberry Pi.

/*
	To initiate a file push from your Mac to a Raspberry Pi where you've set up an OBEX server and ensured the devices are paired, trusted, and connected, you can use the Bluetooth File Exchange application on macOS. This application is built into macOS and allows you to send files to connected Bluetooth devices. Here's how you can do it:

	Step 1: Start OBEX Server on Raspberry Pi
	Before initiating the push from your Mac, make sure the OBEX server is running on your Raspberry Pi. This usually involves starting the obexd service, which listens for incoming file transfers. Depending on your setup, this might already be running or you might need to start it manually.

	Step 2: Locate Bluetooth File Exchange on Mac
	Open Finder on your Mac.
	Navigate to the Go menu and select Utilities, or press Shift + Command + U to open the Utilities folder.
	Find and open Bluetooth File Exchange.
	Step 3: Send the File
	In the Bluetooth File Exchange application, you'll be prompted to select the file you wish to send. Navigate to the file's location, select it, and click Send.
	The application will then search for available Bluetooth devices. Select your Raspberry Pi from the list of devices and proceed.
	If everything is set up correctly, your Raspberry Pi should receive the file. Depending on your OBEX server configuration, you might need to accept the file transfer on the Raspberry Pi.
	Troubleshooting
	Ensure OBEX Server is Running: Double-check that the OBEX server is correctly set up and running on your Raspberry Pi.
*/

// device, err := btm.adapter.GetDeviceByAddress(deviceAddress)
// if err != nil {
// 	return fmt.Errorf("Failed to get device by address for trust and connect: %v", err)
// }
// err = device.SetTrusted(true)
// if err != nil {
// 	return fmt.Errorf("Failed to set device as trusted: %v", err)
// }
// err = device.Connect()
// if err != nil {
// 	return fmt.Errorf("Failed to connect to device: %v", err)
// }



func (btm *bluetoothManager) ControlOBEXServer(start bool) error {
    // Check the current status of obexd
    pgrepCmd := exec.Command("pgrep", "obexd")
	pidBytes, err := pgrepCmd.Output()
    isActive := err == nil

    // Decide whether to start or stop based on the desired state and current status
    if start && isActive {
		btm.l.Debugln("obexd is already running.")
        return nil
    } else if !start && !isActive {
		btm.l.Debugln("obexd is already stopped.")
        return nil
    }

	var cmd *exec.Cmd
	if start {
		// Command to start the obexd service
		btm.l.Debugln("Starting obexd service... ")
		cmd = exec.Command("obexd", "-a", "-r", "/home/sunlight/sunlight-meter")
	} else {
		// Command to stop the obexd service
		btm.l.Debugln("Stopping obexd service...")
		cmd = exec.Command("killall", "obexd")
	}

	// Execute the command
	if err := cmd.Start(); err != nil {
		if start {
			return fmt.Errorf("failed to start obexd: %v", err)
		} else {
			return fmt.Errorf("failed to stop obexd: %v", err)
		}
	} else {
		if start {
			btm.l.Infof("obexd [PID: %d] started successfully\n", cmd.Process.Pid)
		} else {
			btm.l.Infof("obexd [PID: %s] stopped successfully\n", string(pidBytes))
		}
	}
    return nil
}