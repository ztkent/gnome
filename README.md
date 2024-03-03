# Sunlight Meter
An integration with the Adafruit TSL2591 Digital Light Sensor and Raspberry Pi to monitor daily sunlight conditions.

## About
The TSL2591 is a high dynamic range digital light sensor.  
It can read infrared, full-spectrum or human-visible light, and transmit that data over a serial connection.   
![image](https://github.com/Ztkent/sunlight-meter/assets/7357311/0c8c0c73-f0e5-4973-af64-10f02fd41fb1).  


This project monitors and records that data from each of the three sources.  
With the collected data, we are able to do a few things:  
- Report real-time data for monitoring or automation.
- Create graphics to display lux measurements throughout the day.
- Use historical data to give insight on changes over time.
- Provde a dashboard to display the data in a user-friendly way.
- Most importantly, determine if your location is: ☁️ shade, partial shade, partial sun, or full sun ☀️

## How it works
The TSL2591 sensor is connected to a Raspberry Pi via i2c.
- Go to `raspi-config`
- Select Interfacing Options
- Select I2C
- Enable I2C

Connect the sensor to the Pi:
- Vin to 3.3V
- GND to GND
- SDA to SDA
- SCL to SCL

Find your i2c address, it should be `0x29` by default:
- `sudo i2cdetect -y 1`

## Understanding Lux
<img width="400" alt="image" src="https://github.com/Ztkent/sunlight-meter/assets/7357311/f4ba0f6f-eb35-4d8b-86a6-11862363be98">

