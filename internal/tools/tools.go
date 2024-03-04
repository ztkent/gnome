package tools

import (
	"log"
	"os"
)

func DebugLog(message string) {
	if os.Getenv("LOG_LEVEL") == "debug" {
		log.Println(message)
	}
}
