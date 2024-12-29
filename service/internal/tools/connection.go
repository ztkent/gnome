package tools

import (
	"bytes"
	"crypto/rand"
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

	pitooth "github.com/ztkent/pitooth"
)

const (
	TRANSFER_DIRECTORY = "/home/gnome/transfers"
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

func ManageWIFI() error {
	log.Println("Starting WIFI management...")

	// Use device mac to create distinct bt device names
	activeMacs, err := GetAllActiveMACAddresses()
	if err != nil || len(activeMacs) == 0 {
		log.Println("Failed to get active MAC addresses, falling back to random MAC")
		activeMacs = []string{GenerateRandomMAC()}
	}

	btm, err := pitooth.NewBluetoothManager("Gnome" + "_" + strings.Join(strings.Split(activeMacs[0], ":"), ""))
	if err != nil {
		return fmt.Errorf("Failed to create Bluetooth manager: %v", err)
	}
	defer btm.Stop()

	// Accept OBEX file transfers
	// This needs to be open first, so pairing devices will idenify the capability.
	log.Println("Starting OBEX server")
	if err := btm.ControlOBEXServer(true, TRANSFER_DIRECTORY); err != nil {
		return fmt.Errorf("Failed to start OBEX server: %v", err)
	}
	defer btm.ControlOBEXServer(false, "")

	// Accept Bluetooth connections
	log.Printf("Now accepting devices via Bluetooth\n")
	return manageWIFI(btm)
}

func manageWIFI(btm pitooth.BluetoothManager) error {
	// Watch for new credentials
	for {
		go btm.AcceptConnections(time.Second * 30)
		creds, err := watchForCreds(time.Second * 30)
		if err != nil {
			fmt.Printf("Failed to receive wifi credentials: %v", err)
			continue
		} else if creds == nil {
			// Keep watching for new creds
			continue
		}

		// Connect to the provided creds with nmcli
		err = attemptWifiConnection(*creds)
		if err != nil {
			log.Printf("Failed to connect to the target Wi-Fi network: %v\n", err)
			continue
		}
	}
}

func watchForCreds(timeout time.Duration) (*Credentials, error) {
	cleanUpTransfers()
	log.Println("Watching for new files in ", TRANSFER_DIRECTORY)
	timeoutTimer := time.NewTimer(timeout)
	retryTicker := time.NewTicker(5 * time.Second)
	defer retryTicker.Stop()
	for {
		select {
		case <-timeoutTimer.C:
			return nil, nil
		case <-retryTicker.C:
			creds, err := processDirectory(TRANSFER_DIRECTORY)
			if err != nil {
				return nil, fmt.Errorf("Error processing creds directory: %v", err)
			}
			if creds.SSID != "" && creds.Password != "" {
				return &creds, nil
			}
		}
	}
}

func processDirectory(dirPath string) (Credentials, error) {
	files, err := os.ReadDir(dirPath)
	if err != nil {
		return Credentials{}, fmt.Errorf("Error reading directory: %v", err)
	}

	foundCreds := Credentials{}
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
				foundCreds = *creds
				break
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

// Use nmcli to connect to the provided Wi-Fi network credentials
func attemptWifiConnection(creds Credentials) error {
	log.Printf("Attempting to connect to Wi-Fi network: %s\n", creds.SSID)

	// Recan for available networks
	_, err := runCommand("nmcli", "device", "wifi", "rescan")
	if err != nil {
		return fmt.Errorf("Failed to rescan Wi-Fi networks: %v", err)
	}

	// Add new Wi-Fi connection
	_, err = runCommand("nmcli", "dev", "wifi", "connect", creds.SSID, "password", creds.Password)
	if err != nil {
		return fmt.Errorf("failed to connect to Wi-Fi network %s: %v", creds.SSID, err)
	}

	// Ensure our network matches the SSID we requested
	currentSSID, err := GetCurrentSSID()
	if err != nil {
		return fmt.Errorf("Failed to get current SSID: %v", err)
	}
	if currentSSID != creds.SSID {
		return fmt.Errorf("Connected to %s, not %s", currentSSID, creds.SSID)
	}
	log.Printf("Connected to Wi-Fi network: %s\n", currentSSID)
	return nil
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
		_, err := GetCurrentSSID()
		if err != nil {
			log.Println("Failed to get current SSID:", err)
		}
	} else {
		log.Println("Not connected to the internet")
	}
	return connected
}

func GetCurrentSSID() (string, error) {
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

func GetOutboundIP() net.IP {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err != nil {
		return net.IPv4(127, 0, 0, 1)
	}
	defer conn.Close()
	localAddr := conn.LocalAddr().(*net.UDPAddr)
	return localAddr.IP
}

// GenerateRandomMAC generates a random MAC address.
func GenerateRandomMAC() string {
	mac := make([]byte, 6)
	rand.Read(mac)

	// Set the locally administered bit (second least significant bit of the first byte)
	// and ensure the unicast bit (least significant bit of the first byte) is 0.
	mac[0] = (mac[0] | 0x02) & 0xfe
	return fmt.Sprintf("%02x:%02x:%02x:%02x:%02x:%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5])
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
