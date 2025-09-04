package tools

import (
	"database/sql"
	"encoding/csv"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"time"
)

// Prevent out-of-network requests to dashboard endpoints
func CheckInNetwork(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ip, _, err := net.SplitHostPort(r.RemoteAddr)
		if err != nil {
			http.Error(w, "Invalid request", http.StatusBadRequest)
			return
		}
		parsedIP := net.ParseIP(ip)
		if parsedIP == nil {
			http.Error(w, "Invalid IP address", http.StatusBadRequest)
			return
		}
		if !isLocalAddress(parsedIP) {
			http.Error(w, "Access denied", http.StatusForbidden)
			return
		}

		next.ServeHTTP(w, r)
	})
}
func isLocalAddress(ip net.IP) bool {
	privateBlocks := []string{
		"10.0.0.0/8",
		"172.16.0.0/12",
		"192.168.0.0/16",
	}
	for _, block := range privateBlocks {
		_, cidr, _ := net.ParseCIDR(block)
		if cidr.Contains(ip) {
			return true
		}
	}
	return ip.String() == "127.0.0.1"
}

// Get the start and end dates from the request, format them for comparison with the DB
func ParseStartAndEndDate(r *http.Request) (string, string) {
	r.ParseForm()
	startDate := r.FormValue("start")
	endDate := r.FormValue("end")
	layoutInput := "2006-01-02T15:04"
	layoutDB := "2006-01-02 15:04:05"
	if startDate == "" || endDate == "" {
		startDate = time.Now().UTC().Add(-8 * time.Hour).Format(layoutDB)
		endDate = time.Now().UTC().Format(layoutDB)
	} else {
		var err error
		var t time.Time

		// Assume they are in EST, who has users? Not me.
		loc, _ := time.LoadLocation("America/Indiana/Indianapolis")

		t, err = time.Parse(layoutInput, startDate)
		if err != nil {
			log.Println("Error parsing start date:", err)
		} else {
			t = time.Date(t.Year(), t.Month(), t.Day(), t.Hour(), t.Minute(), t.Second(), t.Nanosecond(), loc)
			startDate = t.UTC().Format(layoutDB)
		}

		t, err = time.Parse(layoutInput, endDate)
		if err != nil {
			log.Println("Error parsing end date:", err)
		} else {
			t = time.Date(t.Year(), t.Month(), t.Day(), t.Hour(), t.Minute(), t.Second(), t.Nanosecond(), loc)
			endDate = t.UTC().Format(layoutDB)
		}
	}
	return startDate, endDate
}
func StartAndEndDateToTime(startDate string, endDate string) (time.Time, time.Time, error) {
	layoutDB := "2006-01-02 15:04:05"
	start, err := time.Parse(layoutDB, startDate)
	if err != nil {
		return time.Time{}, time.Time{}, err
	}
	end, err := time.Parse(layoutDB, endDate)
	if err != nil {
		return time.Time{}, time.Time{}, err
	}
	return start, end, nil
}

func ExportToCSV(dbFile, csvFile string) error {
	db, err := sql.Open("sqlite3", dbFile)
	if err != nil {
		return fmt.Errorf("failed to open database: %w", err)
	}
	defer db.Close()

	rows, err := db.Query(`SELECT id, job_id, lux, full_spectrum, visible, infrared, created_at FROM sunlight`)
	if err != nil {
		return fmt.Errorf("failed to query database: %w", err)
	}
	defer rows.Close()

	file, err := os.Create(csvFile)
	if err != nil {
		return fmt.Errorf("failed to create CSV file: %w", err)
	}
	defer file.Close()

	writer := csv.NewWriter(file)
	defer writer.Flush()

	// Write CSV header
	header := []string{"id", "job_id", "lux", "full_spectrum", "visible", "infrared", "created_at"}
	if err := writer.Write(header); err != nil {
		return fmt.Errorf("failed to write CSV header: %w", err)
	}

	// Write CSV rows
	for rows.Next() {
		var id int
		var jobID, lux, fullSpectrum, visible, infrared, createdAt string

		if err := rows.Scan(&id, &jobID, &lux, &fullSpectrum, &visible, &infrared, &createdAt); err != nil {
			return fmt.Errorf("failed to scan row: %w", err)
		}

		record := []string{
			fmt.Sprintf("%d", id),
			jobID,
			lux,
			fullSpectrum,
			visible,
			infrared,
			createdAt,
		}

		if err := writer.Write(record); err != nil {
			return fmt.Errorf("failed to write CSV record: %w", err)
		}
	}

	if err := rows.Err(); err != nil {
		return fmt.Errorf("row iteration error: %w", err)
	}
	return nil
}

func ExportToJSON(dbFile string, start time.Time, end time.Time) ([]map[string]interface{}, error) {
	db, err := sql.Open("sqlite3", dbFile)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}
	defer db.Close()

	rows, err := db.Query(`SELECT id, job_id, lux, full_spectrum, visible, infrared, created_at FROM sunlight WHERE created_at BETWEEN ? AND ?`, start.UTC(), end.UTC())
	if err != nil {
		return nil, fmt.Errorf("failed to query database: %w", err)
	}
	defer rows.Close()

	var results []map[string]interface{}

	for rows.Next() {
		var id int
		var jobID, lux, fullSpectrum, visible, infrared, createdAt string

		if err := rows.Scan(&id, &jobID, &lux, &fullSpectrum, &visible, &infrared, &createdAt); err != nil {
			return nil, fmt.Errorf("failed to scan row: %w", err)
		}

		record := map[string]interface{}{
			"id":            id,
			"job_id":        jobID,
			"lux":           lux,
			"full_spectrum": fullSpectrum,
			"visible":       visible,
			"infrared":      infrared,
			"created_at":    createdAt,
		}

		results = append(results, record)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("row iteration error: %w", err)
	}

	return results, nil
}

// ExportEnvironmentalToCSV exports environmental data to CSV format as a string
func ExportEnvironmentalToCSV(dbFile string) (string, error) {
	db, err := sql.Open("sqlite3", dbFile)
	if err != nil {
		return "", fmt.Errorf("failed to open database: %w", err)
	}
	defer db.Close()

	rows, err := db.Query(`SELECT id, job_id, temperature, humidity, pressure, created_at FROM environmental`)
	if err != nil {
		return "", fmt.Errorf("failed to query database: %w", err)
	}
	defer rows.Close()

	csvData := "id,job_id,temperature,humidity,pressure,created_at\n"

	for rows.Next() {
		var id int
		var jobID, temperature, humidity, pressure, createdAt string

		if err := rows.Scan(&id, &jobID, &temperature, &humidity, &pressure, &createdAt); err != nil {
			return "", fmt.Errorf("failed to scan row: %w", err)
		}

		csvData += fmt.Sprintf("%d,%s,%s,%s,%s,%s\n", 
			id, jobID, temperature, humidity, pressure, createdAt)
	}

	if err := rows.Err(); err != nil {
		return "", fmt.Errorf("row iteration error: %w", err)
	}

	return csvData, nil
}

// ExportEnvironmentalToJSON exports environmental data to JSON format
func ExportEnvironmentalToJSON(dbFile string, start time.Time, end time.Time) (string, error) {
	db, err := sql.Open("sqlite3", dbFile)
	if err != nil {
		return "", fmt.Errorf("failed to open database: %w", err)
	}
	defer db.Close()

	rows, err := db.Query(`SELECT id, job_id, temperature, humidity, pressure, created_at FROM environmental WHERE created_at BETWEEN ? AND ?`, start.UTC(), end.UTC())
	if err != nil {
		return "", fmt.Errorf("failed to query database: %w", err)
	}
	defer rows.Close()

	var results []map[string]interface{}

	for rows.Next() {
		var id int
		var jobID, temperature, humidity, pressure, createdAt string

		if err := rows.Scan(&id, &jobID, &temperature, &humidity, &pressure, &createdAt); err != nil {
			return "", fmt.Errorf("failed to scan row: %w", err)
		}

		record := map[string]interface{}{
			"id":          id,
			"job_id":      jobID,
			"temperature": temperature,
			"humidity":    humidity,
			"pressure":    pressure,
			"created_at":  createdAt,
		}

		results = append(results, record)
	}

	if err := rows.Err(); err != nil {
		return "", fmt.Errorf("row iteration error: %w", err)
	}

	// Convert results to JSON string
	jsonData := "["
	for i, record := range results {
		if i > 0 {
			jsonData += ","
		}
		jsonData += fmt.Sprintf(`{"id":%d,"job_id":"%s","temperature":"%s","humidity":"%s","pressure":"%s","created_at":"%s"}`,
			record["id"], record["job_id"], record["temperature"], record["humidity"], record["pressure"], record["created_at"])
	}
	jsonData += "]"

	return jsonData, nil
}
