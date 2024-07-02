package tools

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	pitooth "github.com/Ztkent/pitooth"
)

const (
	TRANSFER_DIRECTORY = "/home/sunlight/transfers"
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

func ManageInternetConnection() error {
	if !checkInternetConnection("http://www.google.com") {
		log.Println("No internet connection detected. Starting WIFI management...")
		btm, err := pitooth.NewBluetoothManager("SunlightMeter")
		if err != nil {
			return fmt.Errorf("Failed to create Bluetooth manager: %v", err)
		}
		defer btm.Close(false)

		// Accept OBEX file transfers
		// This needs to be open first, so pairing devices will idenify the capability.
		log.Println("Starting OBEX server")
		if err := btm.ControlOBEXServer(true, TRANSFER_DIRECTORY); err != nil {
			return fmt.Errorf("Failed to start OBEX server: %v", err)
		}
		defer btm.ControlOBEXServer(false, "")

		// Accept Bluetooth connections
		log.Printf("Attempting to accept Bluetooth connections\n")
		var connectedDevices map[string]pitooth.Device
		for attempt := 1; attempt <= 5; attempt++ {
			var err error
			connectedDevices, err = btm.AcceptConnections(30 * time.Second)
			if err != nil {
				log.Printf("Attempt %d: Failed to accept Bluetooth connections: %v\n", attempt, err)
			} else if len(connectedDevices) == 0 {
				log.Printf("Attempt %d: No devices connected via Bluetooth\n", attempt)
			} else {
				log.Printf("Attempt %d: Successfully connected to %d devices via Bluetooth\n", attempt, len(connectedDevices))
				break
			}
		}
		if len(connectedDevices) == 0 {
			return fmt.Errorf("No devices connected via Bluetooth")
		}

		// Watch for new credentials
		creds, err := watchForCreds(time.Second * 180)
		if err != nil {
			return fmt.Errorf("Failed to receive wifi credentials: %v", err)
		} else if len(creds) == 0 {
			return fmt.Errorf("No wifi credentials received")
		}

		// Connect to the provided creds with nmcli
		err = attemptWifiConnection(creds)
		if err != nil {
			return fmt.Errorf("Failed to connect to Wi-Fi network: %v", err)
		}

		// Log the SSID we're connected to
		currentSSID, err := getCurrentSSID()
		if err != nil {
			return fmt.Errorf("Failed to get current SSID: %v", err)
		} else {
			log.Printf("Successfully connected to Wi-Fi network: %s\n", currentSSID)
		}
	}
	return nil
}

func watchForCreds(timeout time.Duration) ([]*Credentials, error) {
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
			if len(creds) > 0 {
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
		if file.IsDir() {
			continue
		}
		if filepath.Ext(file.Name()) == ".creds" {
			log.Println("Processing file:", file.Name())
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

// This uses the wpa_supplicant.conf file to add the Wi-Fi network credentials, then restarts the networking service
func attemptWifiConnection(creds []*Credentials) error {
	for _, cred := range creds {
		log.Printf("Attempting to connect to Wi-Fi network: %s\n", cred.SSID)

		// Recan for available networks
		_, err := runCommand("nmcli", "device", "wifi", "rescan")
		if err != nil {
			log.Printf("Failed to rescan Wi-Fi networks: %v\n", err)
		}

		// Add new Wi-Fi connection
		_, err = runCommand("nmcli", "dev", "wifi", "connect", cred.SSID, "password", cred.Password)
		if err != nil {
			return fmt.Errorf("failed to connect to Wi-Fi network %s: %v", cred.SSID, err)
		}

		if checkInternetConnection("http://www.google.com") {
			return nil
		}
	}
	return fmt.Errorf("Failed to connect to any Wi-Fi network")
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
		_, err := getCurrentSSID()
		if err != nil {
			log.Println("Failed to get current SSID:", err)
		}
	} else {
		log.Println("Not connected to the internet")
	}
	return connected
}

func getCurrentSSID() (string, error) {
	output, err := runCommand("iwgetid", "-r")
	if err != nil {
		return "", err
	}
	ssid := strings.TrimSpace(string(output))
	// Log SSID and our IP address
	if ssid != "" {
		log.Println("Connected to ", ssid, " : ", GetOutboundIP())
	}

	return ssid, nil
}

// https://stackoverflow.com/questions/23558425/how-do-i-get-the-local-ip-address-in-go
func GetOutboundIP() net.IP {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err != nil {
		return net.IPv4(127, 0, 0, 1)
	}
	defer conn.Close()
	localAddr := conn.LocalAddr().(*net.UDPAddr)
	return localAddr.IP
}

func GetAllActiveMACAddresses() ([]string, error) {
    var macAddresses []string
    ifaces, err := net.Interfaces()
    if err != nil {
        return nil, err
    }

    for _, iface := range ifaces {
        // Check if the interface is up and ignore loopback
        if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
            continue
        }
        if iface.HardwareAddr != nil {
            macAddresses = append(macAddresses, iface.HardwareAddr.String())
        }
    }

    return macAddresses, nil
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

func runCommand(name string, args ...string) (string, error) {
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	cmd := exec.Command(name, args...)
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		return "", fmt.Errorf("%v: %s", err, stderr.String())
	}
	return stdout.String(), nil
}
