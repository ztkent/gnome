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

## Enable I2C Interface: Use the raspi-config tool to enable the I2C interface
```sh
- sudo raspi-config
- Interfacing Options > I2C > Yes
- reboot
```


## Run at Startup
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
ExecStart=/home/gnome/gnome/service/gnome
Environment="PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/games:/usr/games:/home/gnome/go/bin:/usr/libexec/bluetooth"
WorkingDirectory=/home/gnome/gnome/service
User=root
Restart=always

[Install]
WantedBy=multi-user.target
```

Reload systemd to recognize the new service:
`sudo systemctl daemon-reload`

Enable the service to start on boot:
`sudo systemctl enable gnome.service`

Start the service immediately:
`sudo systemctl start gnome.service`

Check the status of the service:
`sudo systemctl status gnome.service`

## Remote Wifi Management

Use [PiFi](https://github.com/ztkent/pifi) to manage WiFi connections on the Raspberry Pi.
