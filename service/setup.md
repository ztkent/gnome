# Setup Instructions
## Build the app
```shell
docker run -v ".":/usr/src/myapp -w /usr/src/myapp arm64v8/alpine:latest sh -c '
  apk update &&
  apk add gcc musl-dev curl &&
  tar -C /usr/local -xzf go1.21.11.linux-arm64.tar.gz &&
  export PATH=$PATH:/usr/local/go/bin &&
  CGO_ENABLED=1 GOOS=linux GOARCH=arm64 go build -a -installsuffix cgo -ldflags '\''-extldflags "-static"'\'' -o slm .'
```

## Configure the Pi
### Update the system
```shell
sudo apt update
```

### Clone the repo, if you'd like.
```shell
git clone https://github.com/ztkent/gnome
```

### Setup Golang
```shell
wget https://go.dev/dl/go1.21.11.linux-arm64.tar.gz
sudo rm -rf /usr/local/go && sudo tar -C /usr/local -xzf go1.21.11.linux-arm64.tar.gz && rm go1.21.11.linux-arm64.tar.gz
echo "export PATH=\$PATH:/usr/local/go/bin" >> ~/.bashrc
source ~/.bashrc
```

### Set GOPATH, GOCACHE, and GOBIN
```shell
echo "export GOPATH=\$HOME/go" >> ~/.bashrc
echo "export GOCACHE=\$HOME/.cache/go-build" >> ~/.bashrc
echo "export GOBIN=\$GOPATH/bin" >> ~/.bashrc
echo "export PATH=\$PATH:\$GOBIN" >> ~/.bashrc
source ~/.bashrc
```

### Add OBEXD
```shell
sudo apt install bluez-obexd
echo 'export PATH=$PATH:/usr/libexec/bluetooth' >> ~/.bashrc
source ~/.bashrc
```

### Enable I2C Interface: Use the raspi-config tool to enable the I2C interface.
```shell
- `sudo raspi-config`
- Interfacing Options > I2C > Yes
- reboot
```

### Install the app and set it to run at boot
```shell
sudo mv slm /home/gnome/
mkdir /home/gnome/transfers
source /home/gnome/.bashrc
sudo chmod +x /home/gnome/slm
sudo crontab -e
  @reboot /home/gnome/slm
reboot
```