# Setup Instructions

## Configure the Pi

```sh
# Install dependencies
sudo apt update
sudo apt install git make vim

curl -OL https://golang.org/dl/go1.23.4.linux-arm64.tar.gz &&
tar -C /home/gnome -xzf go1.23.4.linux-arm64.tar.gz


sudo apt update
sudo apt install gh
gh auth login
```

### Set GOPATH, GOCACHE, and GOBIN

```sh
echo "export GOPATH=/home/gnome/go" >> ~/.bashrc
echo "export GOROOT=/home/gnome/go" >> ~/.bashrc
echo "export GOCACHE=\$HOME/.cache/go-build" >> ~/.bashrc
echo "export GOBIN=\$GOROOT/bin" >> ~/.bashrc
echo "export PATH=\$PATH:\$GOBIN:\$GOPATH" >> ~/.bashrc
source /home/gnome/.bashrc
```

### Enable I2C Interface: Use the raspi-config tool to enable the I2C interface

```sh
- sudo raspi-config
- Interfacing Options > I2C > Yes
- reboot
```

### Clone, Build

```sh
git clone https://github.com/ztkent/gnome.git

Cross Compile:
  docker run -v "$(pwd)":/usr/src/myapp -w /usr/src/myapp debian:bookworm-slim sh -c '
    apt-get update &&
    apt-get install -y gcc musl-dev curl &&
    curl -OL https://golang.org/dl/go1.23.4.linux-arm64.tar.gz &&
    tar -C /usr/local -xzf go1.23.4.linux-arm64.tar.gz &&
    export PATH=$PATH:/usr/local/go/bin &&
    CGO_ENABLED=1 GOOS=linux GOARCH=arm64 \
    go build -o gnome_aarch64 .'

Local Build:
  go build -x -v -gcflags="all=-N -l" -o gnome
```

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

### Remote Wifi Management

Use [PiFi](https://github.com/ztkent/pifi) to manage WiFi connections on the Raspberry Pi.
