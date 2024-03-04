# Sunlight Meter
Using the Adafruit TSL2591 Digital Light Sensor and a Raspberry Pi to monitor daily sunlight conditions.  


## About
The [TSL2591](https://www.adafruit.com/product/1980) is a high dynamic range digital light sensor.  
It can read infrared, full-spectrum or human-visible light, and transmit that data over a serial connection.   


This project monitors and records data from each of the three sources.  
With the collected data, we are able to:  
- Calculate the lux value for the current light conditions.
- Report real-time data for monitoring or automation.
- Use historical data to give insight on changes over time.
- Provde a dashboard to display the data in a user-friendly way.
- Most importantly, determine if your location is: ☁️ shade, partial shade, partial sun, or full sun ☀️

## How it works
The TSL2591 sensor is connected to a Raspberry Pi via i2c.  
Connecting the sensor to the Pi:
- Vin to 3.3V
- GND to GND
- SDA to SDA
- SCL to SCL

"Sunlight Meter" runs a web server on device to allow remote access to the sensor data and jobs.  
Connect remotely to:
- Start/Stop any recording job.
- Report real-time readings and light conditions. 
- Export historical data to a CSV file for download.
- Check device wifi-signal strength.

## Understanding Lux Values
<img width="400" alt="image" src="https://github.com/Ztkent/sunlight-meter/assets/7357311/f4ba0f6f-eb35-4d8b-86a6-11862363be98">

