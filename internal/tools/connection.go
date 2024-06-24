package tools

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
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
		// Create a new Bluetooth manager
		btm, err := pitooth.NewBluetoothManager("SunlightMeter")
		if err != nil {
			log.Println("Failed to create Bluetooth manager:", err)
			return
		}
		defer btm.Close(true)

		// Get the client to connect to the pi w/ bluetooth
		log.Printf("Attempting to accept Bluetooth connections\n")
		for attempt := 1; attempt <= 5; attempt++ {
			connectedDevices, err := btm.AcceptConnections(30 * time.Second)
			if err != nil {
				log.Printf("Attempt %d: Failed to accept Bluetooth connections: %v\n", attempt, err)
			} else if len(connectedDevices) == 0 {
				log.Printf("Attempt %d: No devices connected via Bluetooth\n", attempt)
			} else {
				break
			}
		}

		// Start the OBEX server, accept file transfers
		log.Println("Starting OBEX server")
		if err := btm.ControlOBEXServer(true, "/home/sunlight/sunlight-meter/transfers"); err != nil {
			log.Println("Failed to start OBEX server:", err)
			return
		}
		defer btm.ControlOBEXServer(false, "")

		// Watch /transfers for new files
		var creds *Credentials
		log.Println("Watching for new files in /home/sunlight/sunlight-meter/transfers")
		for {
			var err error
			creds, err = processDirectory("/home/sunlight/sunlight-meter/transfers")
			if err != nil {
				log.Println("Error processing directory:", err)
				continue
			}
			if creds != nil {
				break
			}
			time.Sleep(5 * time.Second)
		}

		if creds.Username == "" || creds.Password == "" {
			log.Println("No credentials found in files")
			return
		}

		// Connect to the Wi-Fi network
		log.Println("Attempting to connect to Wi-Fi network")
		if err := AddWifiNetwork(creds.Username, creds.Password); err != nil {
			log.Println("Failed to add Wi-Fi network:", err)
			return
		}
		logWpaSupplicantContents()

		// Restart the networking service
		log.Println("Restarting networking service")
		cmd := exec.Command("systemctl", "restart", "networking")
		if err := cmd.Run(); err != nil {
			log.Println("Failed to restart networking service:", err)
			return
		}

		// Check if the Pi is connected to the internet
		if !CheckInternetConnection() {
			log.Println("Failed to connect to Wi-Fi network")
			return
		}
		currentSSID, err := GetCurrentSSID()
		if err != nil {
			log.Println("Failed to get current SSID:", err)
		} else {
			log.Printf("Successfully connected to Wi-Fi network: %s\n", currentSSID)
		}
	}
	return
}

type Credentials struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

func processDirectory(dirPath string) (*Credentials, error) {
	files, err := os.ReadDir(dirPath)
	if err != nil {
		return nil, err
	}
	for _, file := range files {
		if file.IsDir() {
			continue
		}
		if filepath.Ext(file.Name()) == ".json" {
			fullPath := filepath.Join(dirPath, file.Name())
			creds, err := readCredentials(fullPath)
			if err != nil {
				log.Printf("Error reading JSON from %s: %v\n", fullPath, err)
				continue
			}
			if creds.Username != "" && creds.Password != "" {
				log.Printf("Found credentials in %s: %+v\n", fullPath, creds)
				return creds, nil
			}
		}
	}
	return nil, nil
}

func readCredentials(filePath string) (*Credentials, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return nil, err
	}

	var creds Credentials
	err = json.Unmarshal(data, &creds)
	if err != nil {
		return nil, err
	}

	return &creds, nil
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
