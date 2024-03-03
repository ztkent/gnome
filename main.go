package main

import (
	"fmt"
	"log"
	"time"

	"github.com/Ztkent/sunlight-meter/internal/tools"
	"github.com/Ztkent/sunlight-meter/tsl2591"
)

func main() {
	println("Bright, sunny day!")
	tsl, err := tsl2591.NewTSL2591(&tsl2591.Opts{
		Gain:    tsl2591.TSL2591_GAIN_LOW,
		Timing:  tsl2591.TSL2591_INTEGRATIONTIME_600MS,
		DevPath: "/dev/i2c-1",
	})
	if err != nil {
		panic(err)
	}
	tsl.Enable()
	defer tsl.Disable()

	ticker := time.NewTicker(1 * time.Second)
	for {
		channel0, channel1, err := tsl.GetFullLuminosity()
		if err != nil {
			log.Fatal(err)
		}
		tools.DebugLog(fmt.Sprintf("0x%04x 0x%04x\n", channel0, channel1))
		lux, err := tsl.CalculateLux(channel0, channel1)
		// TSL2591_VISIBLE      byte = 2 ///< channel 0 - channel 1
		// TSL2591_INFRARED     byte = 1 ///< channel 1
		// TSL2591_FULLSPECTRUM byte = 0 ///< channel 0
		if err != nil {
			log.Fatal(err)
		}
		log.Println(lux)
		<-ticker.C
	}
}
