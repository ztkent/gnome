# Setup Instructions

## Configure the Pi
### Update the system
```shell
# Install dependencies
sudo apt update
sudo apt install git make

curl -OL https://golang.org/dl/go1.23.4.linux-arm64.tar.gz &&
tar -C /usr/local -xzf go1.23.4.linux-arm64.tar.gz

sudo apt update
sudo apt install gh
gh auth login
```

### Set GOPATH, GOCACHE, and GOBIN
```shell
echo "export GOPATH=/home/sunlight/go" >> ~/.bashrc
echo "export GOROOT=/usr/local/go" >> ~/.bashrc
echo "export GOCACHE=\$HOME/.cache/go-build" >> ~/.bashrc
echo "export GOBIN=\$GOROOT/bin" >> ~/.bashrc
echo "export PATH=\$PATH:\$GOBIN:\$GOPATH:" >> ~/.bashrc
source /home/sunlight/.bashrc
```

### Add OBEXD
```shell
sudo apt-get update
sudo apt install bluez-obexd
echo 'export PATH=$PATH:/usr/libexec/bluetooth' >> ~/.bashrc
source /home/sunlight/.bashrc
```

### Enable I2C Interface: Use the raspi-config tool to enable the I2C interface.
```shell
- sudo raspi-config
- Interfacing Options > I2C > Yes
- reboot
```

### Clone, Build
```shell
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

## Run at Startup
Create a new service file in /etc/systemd/system.
```
gnome.service:
sudo nano /etc/systemd/system/gnome.service
```

Add the following content to the service file:
```shell
[Unit]
Description=Gnome Service
After=network.target

[Service]
ExecStart=/home/sunlight/gnome/service/gnome
Environment="PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/games:/usr/games:/usr/local/go/bin:/usr/libexec/bluetooth"
WorkingDirectory=/home/sunlight/gnome/service
User=root
Restart=always

[Install]
WantedBy=multi-user.target
```

```shell
[Unit]
Description=OBEX Object Push daemon
Before=bluetooth.service

[Service]
ExecStart=/usr/lib/bluetooth/obexd -a -r /home/gnome/transfers
Restart=on-failure

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