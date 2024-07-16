# P2PChatApp

P2PChatApp is a peer-to-peer chat application for Android that allows users to send messages over the same Wi-Fi network using TCP/IP socket programming. The app is developed in Kotlin and uses Jetpack Compose for the user interface.

## Features

- Send and receive messages over the same Wi-Fi network.
- Ping functionality to check connectivity between devices.
- Simple and intuitive user interface built with Jetpack Compose.

## Prerequisites

- Android Studio (latest version)
- An Android device 
- Wi-Fi network for peer-to-peer communication

## Installation

1. **Clone the repository**:

## Usage

To use P2PChatApp with two devices or emulators:

1. **Open two instances of the project**:
   - Open Android Studio and load the P2PChatApp project in two separate windows or instances.

2. **Run the app on each device or emulator**:
   - Start one instance on Device A or Emulator A.
   - Start the second instance on Device B or Emulator B.

3. **Configure devices for peer-to-peer communication**:
   - On Device A (acting as server), note its IP address from the Wi-Fi settings.
   - On Device B (acting as client), enter the IP address of Device A in the app's UI.

4. **Send messages**:
   - Use the `Send` button on Device B to send messages to Device A.
   - Check connectivity with the `Ping` button.

5. **Monitor messages received**:
   - Messages sent from Device B should be displayed in the chat log on Device A.

Ensure both devices or emulators are connected to the same Wi-Fi network for proper functionality.

## Developer

This application was developed by Group 12 (LVINSP).
   
