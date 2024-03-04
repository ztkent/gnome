package db

import (
	"database/sql"
	"io/fs"
	"log"
	"os"
	"sort"
	"strings"
	"time"

	_ "github.com/mattn/go-sqlite3" // SQLite driver
)

func ConnectSqlite(filePath string) (*sql.DB, error) {
	// connect to the sqlite database
	db, err := connectWithBackoff("sqlite3", filePath, 3)
	if err != nil {
		return nil, err
	}

	// run the migrations
	err = RunMigrations(db)
	if err != nil {
		return nil, err
	}

	return db, nil
}

func RunMigrations(db *sql.DB) error {
	// Read the migration directory
	files, err := os.ReadDir("internal/migration")
	if err != nil {
		return err
	}

	// Filter and sort the files
	sqlFiles := make([]fs.DirEntry, 0)
	for _, file := range files {
		if strings.HasSuffix(file.Name(), ".sql") {
			sqlFiles = append(sqlFiles, file)
		}
	}
	sort.Slice(sqlFiles, func(i, j int) bool {
		return sqlFiles[i].Name() < sqlFiles[j].Name()
	})

	// Execute each file as a SQL script
	for _, file := range sqlFiles {
		data, err := os.ReadFile("internal/migration/" + file.Name())
		if err != nil {
			return err
		}
		_, err = db.Exec(string(data))
		if err != nil {
			return err
		}
	}
	return nil
}

func connectWithBackoff(driver string, connStr string, maxRetries int) (*sql.DB, error) {
	var db *sql.DB
	var err error
	for i := 0; i < maxRetries; i++ {
		db, err = sql.Open(driver, connStr)
		if err != nil {
			log.Println("Failed attempt to connect to " + driver + ": " + err.Error())
			time.Sleep(time.Duration(i+1) * (3 * time.Second))
			continue
		}
		err = db.Ping()
		if err != nil {
			log.Println("Failed attempt to connect to " + driver + ": " + err.Error())
			time.Sleep(time.Duration(i+1) * (3 * time.Second))
			continue
		}
		return db, nil
	}
	return nil, err
}
