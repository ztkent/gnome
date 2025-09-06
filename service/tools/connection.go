package tools

import (
	"bytes"
	"crypto/rand"
	"fmt"
	"log"
	"net"
	"net/http"
	"os/exec"
	"strings"
	"time"
)

type Credentials struct {
	SSID     string `json:"ssid"`
	Password string `json:"password"`
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
