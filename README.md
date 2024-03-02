# Sunlight Meter
An integration with the Adafruit TSL2591 Digital Light Sensor to monitor daily sunlight conditions.

## About
The TSL2591 is an i2c lux sensor.  
It can read infrared, full-spectrum or human-visible light, and transmit that data over a serial connection.  

This project will monitor and record that data from each of the three sources.  

With the collected data, we are able to do a few things:  
- Report real-time data for monitoring or automation.
- Create graphics to display lux measurements throughout the day.
- Use historical data to give insight on changes over time.
- Provde a dashboard to display the data in a user-friendly way.
- Most importantly, determine if your location is: ☁️ shade, partial shade, partial sun, or full sun ☀️
