package tools

import (
	"fmt"
	"os"
)

func DebugLog(message string) {
	if os.Getenv("LOG_LEVEL") == "debug" {
		fmt.Println(message)
	}
}
