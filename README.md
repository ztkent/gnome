# Gnome - Garden Monitoring System

Used for garden automation, data collection and sensor visualization

## Overview

Gnome is a distributed garden monitoring system that combines:

- **Embedded Devices**: Runs on a Raspberry Pi with configured sensors
- **Web Dashboard**: Browser-based interface for data visualization and device management
- **Android App**: Mobile interface for device discovery, monitoring, and control

## System Architecture

```text
┌─────────────────┐    ┌────────────────────────┐  ┌────────────────────────┐
│   Android App   │    │  Gnome Device          │  │  Gnome Device          │
│                 │    │  (Raspberry Pi)        │  │  (Raspberry Pi)        │
│ - Device Scan   │◄──►│ - TSL2591              │  │ - TSL2591              │
│ - Data Viz      │    │ - SQLite DB            │  │ - SQLite DB            │
│ - Remote Control│    │ - REST API             │  │ - REST API             │
│                 │    │ - Web Dashboard [8080] │  │ - Web Dashboard [8080] │
└─────────────────┘    └────────────────────────┘  └────────────────────────┘
         │                          │                             │
         └──────────────────────────┼─────────────────────────────┘
                                    │
                            ┌─────────────────┐
                            │  Local Network  │
                            │    (WiFi)       │
                            └─────────────────┘
```

## Components

### Service (`/service`)

Embedded Go service that runs on Raspberry Pi devices:

- **Hardware Interface**: Communicates with environmental sensors via I2C
- **Data Collection**: Periodic sensor readings with automatic adjustments
- **Storage**: SQLite database for historical data
- **API**: RESTful endpoints for remote access
- **Dashboard**: Web interface for device management
  - Real-time data visualization and control

### Android App (`/app`)

Kotlin mobile application:

- **Network Discovery**: Automatic scanning for Gnome devices
- **Real-time Monitoring**: Live sensor data display
- **Data Visualization**: Interactive charts and graphs
- **Remote Control**: Start/stop recordings, adjust settings
- **Data Export**: Download historical sensor data

## Quick Start

### Setting up a Gnome Device

1. Follow the instructions in the `/service/setup.md` file to configure the Raspberry Pi and sensors.

### Using the Android App

1. **Install**: Download from releases or build from source
2. **Connect**: Ensure Android device is on same WiFi network
3. **Discover**: App will automatically find Gnome devices
4. **Monitor**: View real-time data and control recordings
