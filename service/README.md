# Gnome - Service

Embeded service that runs on each of the Gnome devices.

## How It Works

- Establishes a connection to the internet
  - Enables Bluetooth + File Transfer
  - Accepts credentials from user device
  - Attempts connection
- Validates a connection to the TSL2591 sensor
- Periodically takes readings from the TSL2591 sensor
- Manages a SQLite database to store sensor readings
- Exposes an API + Dashboard to interact with the device
  - Generates a self-signed certificate for SSL communication with clients

### Configuration

The TSL2591 sensor is connected to a Raspberry Pi via i2c.  
Connecting the sensor to the Pi:

- Vin to 3.3V
- GND to GND
- SDA to SDA
- SCL to SCL

"Gnome" automatically adjusts sensor gain and integration time.  
This helps ensure accurate readings and avoid saturation in high light conditions.  

### API

"Gnome" runs an API that allows remote access to the sensor data and jobs.  
Connect remotely to:

- Start/Stop any recording job.
- Receive real-time readings and light conditions.  
- Download historical data as a SQLite DB.
- Check device wifi-signal strength.

### Dashboard

The "Sunlight Dashboard" is a web app that displays the current light conditions and historical data.  

- Visualize historical light conditions
- Control the sensor
- Export the results
