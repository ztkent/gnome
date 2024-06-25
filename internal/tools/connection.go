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

const (
	TRANSFER_DIRECTORY = "/home/sunlight/sunlight-meter/transfers"
)

type Credentials struct {
	SSID     string `json:"ssid"`
	Password string `json:"password"`
}

/*
	Its essential we are able to connect the pi to WIFI without the user logging in.
	Check if the pi is connected. If not,
	- create a bluetooth manager
	- accept bluetooth connections
	- start an OBEX server
	- watch for new credentials
	- add the credentials to wpa_supplicant.conf
	- restart the networking service
	- check if the pi is connected to the internet

	Hopefully this will get us online.
*/

func ManageInternetConnection() {
	if !checkInternetConnection("") {
		log.Println("No internet connection detected. Starting WIFI management...")
		btm, err := pitooth.NewBluetoothManager("SunlightMeter")
		if err != nil {
			log.Println("Failed to create Bluetooth manager:", err)
			return
		}
		defer btm.Close(true)

		// Accept Bluetooth connections
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

		// Accept OBEX file transfers
		log.Println("Starting OBEX server")
		if err := btm.ControlOBEXServer(true, TRANSFER_DIRECTORY); err != nil {
			log.Println("Failed to start OBEX server:", err)
			return
		}
		defer btm.ControlOBEXServer(false, "")

		// Watch for new credentials
		creds, err := watchForCreds(time.Second * 180)
		if err != nil {
			log.Println("Failed to receive wifi credentials:", err)
			return
		} else if len(creds) == 0 {
			log.Println("No wifi credentials received")
			return
		}

		// If we got credentials, add them to wpa_supplicant.conf
		for _, creds := range creds {
			if err := addWifiNetwork(creds.SSID, creds.Password); err != nil {
				log.Println("Failed to add Wi-Fi network:", err)
				return
			}
		}
		logWpaSupplicantContents()

		// Attempt to restart networking service
		log.Println("Restarting networking service")
		cmd := exec.Command("systemctl", "restart", "networking")
		if err := cmd.Run(); err != nil {
			log.Println("Failed to restart networking service:", err)
			return
		}

		// Restarting the networking service might connect to the Wi-Fi network
		if !checkInternetConnection("http://www.google.com") {
			log.Println("Failed to connect to Wi-Fi network")
			return
		}

		// Log the SSID we're connected to
		currentSSID, err := getCurrentSSID()
		if err != nil {
			log.Println("Failed to get current SSID:", err)
		} else {
			log.Printf("Successfully connected to Wi-Fi network: %s\n", currentSSID)
		}
	}
}

func checkInternetConnection(testSite string) bool {
	client := http.Client{
		Timeout: 10 * time.Second,
	}
	if testSite == "" {
		testSite = "http://www.ztkent.com"
	}
	log.Println("Checking internet connection: ", testSite)
	response, err := client.Get(testSite)
	if err != nil {
		return false
	}
	defer response.Body.Close()
	connected := response.StatusCode == 200
	if connected {
		ssid, err := getCurrentSSID()
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

func getCurrentSSID() (string, error) {
	cmd := exec.Command("iwgetid", "-r")
	output, err := cmd.Output()
	if err != nil {
		return "", err
	}
	ssid := strings.TrimSpace(string(output))
	return ssid, nil
}

func addWifiNetwork(ssid, password string) error {
	log.Println("Attempting to add Wi-Fi network to wpa_supplicant.conf: ", ssid, password)
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

func watchForCreds(timeout time.Duration) ([]*Credentials, error) {
	log.Println("Cleaning up transfers directory")
	cleanUpTransfers()
	log.Println("Watching for new files in ", TRANSFER_DIRECTORY)
	timeoutTimer := time.NewTimer(timeout)
	retryTicker := time.NewTicker(5 * time.Second)
	defer retryTicker.Stop()
	for {
		select {
		case <-timeoutTimer.C:
			return nil, fmt.Errorf("Timed out waiting for credentials")
		case <-retryTicker.C:
			creds, err := processDirectory(TRANSFER_DIRECTORY)
			if err != nil {
				log.Println("Error processing directory:", err)
				return nil, err
			}
			if creds != nil {
				return creds, nil
			}
		}
	}
}

func processDirectory(dirPath string) ([]*Credentials, error) {
	log.Println("Processing directory:", dirPath)
	files, err := os.ReadDir(dirPath)
	if err != nil {
		return nil, err
	}
	foundCreds := []*Credentials{}
	for _, file := range files {
		log.Println("Processing file:", file.Name())
		if file.IsDir() {
			continue
		}
		if filepath.Ext(file.Name()) == ".creds" {
			fullPath := filepath.Join(dirPath, file.Name())
			creds, err := readCredentials(fullPath)
			if err != nil {
				log.Printf("Error reading JSON from %s: %v\n", fullPath, err)
				continue
			}
			if creds.SSID != "" && creds.Password != "" {
				foundCreds = append(foundCreds, creds)
			}
		}
	}
	return foundCreds, nil
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

func cleanUpTransfers() {
	log.Println("Cleaning up transfers directory of .creds files")
	files, _ := filepath.Glob(filepath.Join(TRANSFER_DIRECTORY, "*.creds"))
	for _, file := range files {
		if err := os.Remove(file); err != nil {
			log.Println("Failed to delete .creds file:", file, err)
		}
	}
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
