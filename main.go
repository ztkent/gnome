package main

import (
	"fmt"
	"log"
	"time"

	"github.com/Ztkent/sunlight-meter/tsl2591"
)

const Interval = 1 * time.Second

func main() {
	println("Bright, sunny day!")
	//i2cdetect -l: find connected i2c devices
	// This assumes were at i2c-1
	tsl, err := tsl2591.NewTSL2591(&tsl2591.Opts{
		Gain:   tsl2591.TSL2591_GAIN_LOW,
		Timing: tsl2591.TSL2591_INTEGRATIONTIME_600MS,
	})
	if err != nil {
		panic(err)
	}

	ticker := time.NewTicker(Interval)
	for {
		channel0, channel1, err := tsl.GetFullLuminosity()
		if err != nil {
			log.Fatal(err)
		}
		log.Printf("0x%04x 0x%04x\n", channel0, channel1)
		fmt.Println(tsl.CalculateLux(channel0, channel1))
		<-ticker.C
	}
}
