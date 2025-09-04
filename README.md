# Gnome - Garden Monitoring System

Used for garden automation, data collection and sensor visualization.

## Overview

The Gnome project is a distributed garden monitoring system that combines:

- **Embedded Service**: Runs on a Raspberry Pi with configured sensors
- **Android App**: Mobile interface for device discovery, monitoring, and control
- **Web Dashboard**: Browser-based interface for data visualization and device management

## System Architecture

```text
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Android App   │    │  Gnome Device   │    │  Gnome Device   │
│                 │    │  (Raspberry Pi) │    │  (Raspberry Pi) │
│ - Device Scan   │◄──►│ - TSL2591       │    │ - TSL2591       │
│ - Data Viz      │    │ - SQLite DB     │    │ - SQLite DB     │
│ - Remote Control│    │ - REST API      │    │ - REST API      │
│                 │    │ - Web Dashboard │    │ - Web Dashboard │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                        ┌─────────────────┐
                        │  Local Network  │
                        │    (WiFi)       │
                        └─────────────────┘
```

## Components

### Service (`/service`)

Embedded Go service that runs on Raspberry Pi devices:

- **Hardware Interface**: Communicates with TSL2591 light sensor via I2C
- **Data Collection**: Periodic sensor readings with automatic adjustments
- **Storage**: SQLite database for historical data
- **API**: RESTful endpoints for remote access
- **Dashboard**: Web interface for device management

### Android App (`/app`)

Kotlin/Compose mobile application:

- **Network Discovery**: Automatic scanning for Gnome devices
- **Real-time Monitoring**: Live sensor data display
- **Data Visualization**: Interactive charts and graphs
- **Remote Control**: Start/stop recordings, adjust settings
- **Data Export**: Download historical sensor data

## Quick Start

### Setting up a Gnome Device

1. **TODO**

### Using the Android App

1. **Install**: Download from releases or build from source
2. **Connect**: Ensure Android device is on same WiFi network
3. **Discover**: App will automatically find Gnome devices
4. **Monitor**: View real-time data and control recordings

## Features

### Light Monitoring

- **Accurate Readings**: TSL2591 sensor with automatic gain adjustment
- **Multiple Metrics**: Lux, infrared, visible light measurements
- **Historical Data**: SQLite storage with timestamp tracking
- **Real-time Updates**: Live data streaming to connected clients

### Data Visualization

- **Interactive Charts**: Historical data plotting and analysis
- **Real-time Displays**: Live sensor readings and status
- **Export Options**: Download data in multiple formats
- **Mobile Responsive**: Works on Android devices and web browsers

## Troubleshooting

- **Device Not Found**: Check network connectivity and firewall settings
- **SSL Certificate Errors**: Accept self-signed certificates in browser/app
- **Sensor Not Detected**: Verify I2C wiring and enable I2C on Raspberry Pi
- **App Connection Issues**: Ensure devices are on same WiFi network
