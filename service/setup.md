# Setup Instructions

## Configure the Pi
### Update the system
```shell
# Install dependencies
sudo apt update
sudo apt install git golang-go make

sudo apt update
sudo apt install gh
gh auth login
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

### Clone, Build
```shell
  git clone https://github.com/ztkent/gnome.git

  Cross Compile:
    docker run -v "$(pwd)":/usr/src/myapp -w /usr/src/myapp debian:bookworm-slim sh -c '
      apt-get update &&
      apt-get install -y gcc musl-dev curl &&
      curl -OL https://golang.org/dl/go1.23.0.linux-arm64.tar.gz &&
      tar -C /usr/local -xzf go1.23.0.linux-arm64.tar.gz &&
      export PATH=$PATH:/usr/local/go/bin &&
      CGO_ENABLED=1 GOOS=linux GOARCH=arm64 \
      go build -o gnome_arm64v8 .'

      docker run -v "$(pwd)":/usr/src/myapp -w /usr/src/myapp debian:bookworm-slim sh -c '
        apt-get update &&
        apt-get install -y gcc musl-dev curl &&
        curl -OL https://golang.org/dl/go1.23.0.linux-armv6l.tar.gz &&
        tar -C /usr/local -xzf go1.23.0.linux-armv6l.tar.gz &&
        export PATH=$PATH:/usr/local/go/bin &&
        CGO_ENABLED=1 GOOS=linux GOARCH=arm \
        go build -o gnome_armv6l .'

  Local Build:
    go build -x -v -gcflags="all=-N -l" -o gnome
```