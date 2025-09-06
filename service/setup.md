# Setup Instructions

## Configure the Pi

```sh
sudo apt update
sudo apt install git make vim

curl -OL https://golang.org/dl/go1.25.1.linux-arm64.tar.gz &&
tar -C /usr/local -xzf go1.25.1.linux-arm64.tar.gz

sudo apt update
```

## Set GOPATH, GOCACHE, and GOBIN

```sh
export GOPATH=/usr/local
export GOROOT=/usr/local/go
export GOBIN=$GOROOT/bin
export PATH=$PATH:$GOBIN
source /home/gnome/.bashrc
```

## Enable I2C Interface

### Use the raspi-config tool to enable the I2C interface

```sh
- sudo raspi-config
- Interfacing Options > I2C > Yes
- reboot
```

### Configure the Sensors

<details>
<summary> Connecting the TSL2591 light sensor</summary>

For the first sensor, we can use the default hardware i2c pins. These are preferred as they are typically faster and more reliable.

#### Lux Wiring

- Vin to 3.3V
- GND to GND
- SDA to SDA
- SCL to SCL

#### Verify Lux Sensor Detection

Run the following command to check if the sensor is detected on the I2C bus:

```sh
i2cdetect -y 1
```

You should see the sensor's address (usually 0x29) listed.
</details>

<details>
<summary> Connecting the BEM Temperature and Humidity Sensor </summary>

For the second sensor, we will have to use software I2C.

#### Enable Software I2C, Pins 16/18 (GPIO 23/24)

```sh
sudo vim /boot/firmware/config.txt
```

Add the following to the bottom of the file

```text
[all]
# Enable software I2C bus on GPIO23 (SDA) and GPIO24 (SCL)
dtoverlay=i2c-gpio,bus=2,i2c_gpio_sda=23,i2c_gpio_scl=24

# Enable internal pull-ups for the I2C-gpio pins
gpio=23,24=pu
```

Or on an older (Broadcom) Pi:

```text
[all]
# Enable software I2C bus on GPIO23 (SDA) and GPIO24 (SCL)
dtoverlay=i2c-gpio,bus=2,i2c_gpio_sda=23,i2c_gpio_scl=24,i2c_gpio_pullup=yes
```

#### BEM Wiring

- Vin to 3.3V (Pin 17)
- GND to GND (Pin 20)
- SDA to GPIO 23 (Pin 16)
- SCL to GPIO 24 (Pin 18)

#### Verify BEM Sensor Detection

Run the following command to check if the sensor is detected on the I2C bus:

```sh
i2cdetect -y 2
```

</details>

### Run at Startup

Create a new service file in /etc/systemd/system.

```sh
gnome.service:
sudo nano /etc/systemd/system/gnome.service
```

Add the following content to the service file:

```sh
[Unit]
Description=Gnome Service
After=network.target

[Service]
ExecStart=$HOME/gnome
Environment="PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/go/bin"
WorkingDirectory=$HOME
User=root
Restart=always

[Install]
WantedBy=multi-user.target
```

| Step | Command | Description |
|------|---------|-------------|
| 1 | `sudo systemctl daemon-reload` | Reload systemd to recognize the new service |
| 2 | `sudo systemctl enable gnome.service` | Enable the service to start on boot |
| 3 | `sudo systemctl start gnome.service` | Start the service immediately |
| 4 | `sudo systemctl status gnome.service` | Check the status of the service |


### Remote Wifi Management

You can use [PiFi](https://github.com/ztkent/pifi) to manage WiFi connections on the Raspberry Pi.
