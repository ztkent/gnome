package tools

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"time"

	pitooth "github.com/Ztkent/pitooth"
)

/*
	 Its essential to have a way to connect a pi to the internet, without the user having to login.
		1. Identify if the pi is connected to the internet.
		2. If its not, turn on the bluetooth adapter.
		3. Allow connections to our pi via bluetooth.
		4. Turn on the obexd service.
		5. Connect to the pi from the client (Via the app)
		6. Send the wifi credentials to the pi, via OPP (Object Push Profile)
		7. Attempt to connect to the wifi network using provided credentials.
*/
func ManageInternetConnection() {
	if !CheckInternetConnection() {
		log.Println("No internet connection detected. Starting WIFI management...")
		btm, err := pitooth.NewBluetoothManager("SunlightMeter")
		if err != nil {
			log.Println("Failed to create Bluetooth manager:", err)
			return
		}
		btm.AcceptConnections(30 * time.Second)
	}
	return
}

func CheckInternetConnection() bool {
	client := http.Client{
		Timeout: 10 * time.Second,
	}
	response, err := client.Get("http://www.ztkent.com")
	if err != nil {
		return false
	}
	defer response.Body.Close()
	connected := response.StatusCode == 200
	if connected {
		ssid, err := GetCurrentSSID()
		if err != nil {
			log.Println("Failed to get current SSID:", err)
		} else {
			log.Println("Connected to Wi-Fi network:", ssid)
		}
	} else {
		log.Println("Not connected to the internet")
	}

	return connected
}

func GetCurrentSSID() (string, error) {
	cmd := exec.Command("iwgetid", "-r")
	output, err := cmd.Output()
	if err != nil {
		return "", err
	}
	ssid := strings.TrimSpace(string(output))
	return ssid, nil
}

// AddWifiNetwork adds a Wi-Fi network configuration to the wpa_supplicant.conf file.
func AddWifiNetwork(ssid, password string) error {
	file, err := os.OpenFile("/etc/wpa_supplicant/wpa_supplicant.conf", os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer file.Close()
	networkConfig := fmt.Sprintf("\nnetwork={\n    ssid=\"%s\"\n    psk=\"%s\"\n    key_mgmt=WPA-PSK\n}\n", ssid, password)
	if _, err = file.WriteString(networkConfig); err != nil {
		return err
	}
	return nil
}

// RemoveWifiNetwork removes a Wi-Fi network configuration from the wpa_supplicant.conf file based on the SSID.
func RemoveWifiNetwork(ssid string) error {
	cmdStr := fmt.Sprintf("/network={/,/}/ { /ssid=\"%s\"/,/}/d }", ssid)
	cmd := exec.Command("sed", "-i", cmdStr, "/etc/wpa_supplicant/wpa_supplicant.conf")
	if err := cmd.Run(); err != nil {
		return fmt.Errorf("failed to remove Wi-Fi network: %w", err)
	}
	return nil
}

func logWpaSupplicantContents() {
	content, err := os.ReadFile("/etc/wpa_supplicant/wpa_supplicant.conf")
	if err != nil {
		log.Println("Error reading wpa_supplicant.conf:", err)
		return
	}
	log.Println("Contents of wpa_supplicant.conf:")
	log.Println(string(content))
}
