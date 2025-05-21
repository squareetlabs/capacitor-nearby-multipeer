# @squareetlabs/capacitor-nearby-multipeer

Capacitor plugin for Google Nearby & iOS Multipeer Connectivity. This plugin enables cross-platform peer-to-peer communication between Android and iOS devices using Google Nearby Connections API on Android and Multipeer Connectivity framework on iOS.

## Features

- Cross-platform communication between Android and iOS devices
- Bluetooth support for direct device-to-device communication
- Automatic fallback to Bluetooth when Nearby Connections is not available
- Simple API for advertising, discovery, and message exchange
- Proper permission handling for Android 12+ (API 31+)
- Shared BLE characteristic UUID between Android and iOS for improved cross-platform compatibility

## Install

```bash
npm install @squareetlabs/capacitor-nearby-multipeer
npx cap sync
```

## Basic Usage

Here's a simple example of how to use the plugin:

```typescript
import { NearbyMultipeer } from '@squareetlabs/capacitor-nearby-multipeer';

// Initialize the plugin
async function initializeNearby() {
  try {
    // Initialize with a unique service ID
    await NearbyMultipeer.initialize({ serviceId: 'my-unique-service' });

    // Set the connection strategy (optional)
    await NearbyMultipeer.setStrategy({ strategy: 'P2P_STAR' });

    // Start advertising this device
    await NearbyMultipeer.startAdvertising({ displayName: 'My Device' });

    // Start discovering other devices
    await NearbyMultipeer.startDiscovery();

    console.log('Nearby initialized successfully');
  } catch (error) {
    console.error('Error initializing Nearby:', error);
  }
}

// Connect to a discovered endpoint
async function connectToEndpoint(endpointId: string) {
  try {
    await NearbyMultipeer.connect({ 
      endpointId: endpointId,
      displayName: 'My Device'
    });
    console.log('Connection request sent');
  } catch (error) {
    console.error('Error connecting to endpoint:', error);
  }
}

// Send a message to a connected endpoint
async function sendMessage(endpointId: string, message: string) {
  try {
    await NearbyMultipeer.sendMessage({
      endpointId: endpointId,
      data: message
    });
    console.log('Message sent successfully');
  } catch (error) {
    console.error('Error sending message:', error);
  }
}

// Disconnect from an endpoint
async function disconnect(endpointId: string) {
  await NearbyMultipeer.disconnectFromEndpoint({ endpointId: endpointId });
  console.log('Disconnected from endpoint');
}

// Clean up when done
async function cleanup() {
  await NearbyMultipeer.stopAdvertising();
  await NearbyMultipeer.stopDiscovery();
  await NearbyMultipeer.disconnect(); // Disconnect from all endpoints
  console.log('Cleaned up Nearby resources');
}
```

## Event Handling

This plugin uses events to communicate state changes and incoming data. You should listen for these events to properly handle the peer-to-peer communication:

```typescript
import { NearbyMultipeer } from '@squareetlabs/capacitor-nearby-multipeer';

// Listen for endpoint discovery
const endpointFoundListener = await NearbyMultipeer.addListener('endpointFound', (event) => {
  console.log('Endpoint found:', event.endpointId, event.endpointName);
  // You might want to connect to this endpoint
});

// Listen for connection requests
const connectionRequestedListener = await NearbyMultipeer.addListener('connectionRequested', (event) => {
  console.log('Connection requested from:', event.endpointId, event.endpointName);
  // Decide whether to accept or reject the connection
  NearbyMultipeer.acceptConnection({ endpointId: event.endpointId });
});

// Listen for connection results
const connectionResultListener = await NearbyMultipeer.addListener('connectionResult', (event) => {
  console.log('Connection result for:', event.endpointId, 'Status:', event.status);
  // Status: 0 = success, -1 = error
});

// Listen for incoming messages
const messageListener = await NearbyMultipeer.addListener('message', (event) => {
  console.log('Message from:', event.endpointId, 'Data:', event.data);
  // Process the received message
});

// Listen for disconnections
const endpointLostListener = await NearbyMultipeer.addListener('endpointLost', (event) => {
  console.log('Disconnected from:', event.endpointId);
});

// Listen for transfer updates
const transferUpdateListener = await NearbyMultipeer.addListener('payloadTransferUpdate', (event) => {
  console.log('Transfer update:', {
    endpointId: event.endpointId,
    bytesTransferred: event.bytesTransferred,
    totalBytes: event.totalBytes,
    status: event.status // 2 = in progress, 3 = completed
  });
});

// Remove individual listeners when done
function removeListeners() {
  endpointFoundListener.remove();
  connectionRequestedListener.remove();
  connectionResultListener.remove();
  messageListener.remove();
  endpointLostListener.remove();
  transferUpdateListener.remove();
}

// Or remove all listeners at once
async function cleanup() {
  await NearbyMultipeer.removeAllListeners();
}
```

## Platform Configuration

### Android Configuration

#### Permissions

This plugin requires several permissions to function properly on Android. Add the following permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:maxSdkVersion="30" android:name="android.permission.BLUETOOTH" />
<uses-permission android:maxSdkVersion="30" android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:minSdkVersion="29" android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:minSdkVersion="31" android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:minSdkVersion="31" android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:minSdkVersion="31" android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:minSdkVersion="32" android:name="android.permission.NEARBY_WIFI_DEVICES" />
```

#### Requesting Permissions

For Android 12+ (API 31+), you need to request Bluetooth permissions at runtime. Here's an example of how to do this:

```typescript
import { Permissions } from '@capacitor/core';

async function requestBluetoothPermissions() {
  if (Capacitor.getPlatform() === 'android') {
    const permissions = [
      'android.permission.BLUETOOTH_SCAN',
      'android.permission.BLUETOOTH_CONNECT',
      'android.permission.BLUETOOTH_ADVERTISE'
    ];
    
    for (const permission of permissions) {
      const status = await Permissions.query({ name: permission });
      if (status.state !== 'granted') {
        await Permissions.request({ name: permission });
      }
    }
  }
}

// Call this before initializing the plugin
await requestBluetoothPermissions();
```

### iOS Configuration

For iOS, the plugin uses the Multipeer Connectivity framework which doesn't require special permissions. However, you should add a description for Bluetooth usage in your `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>We use Bluetooth to connect with nearby devices</string>
<key>NSLocalNetworkUsageDescription</key>
<string>We use the local network to discover and connect to nearby devices</string>
```

## Technical Details

### BLE Implementation

The plugin uses Bluetooth Low Energy (BLE) for cross-platform communication between Android and iOS:

- Both platforms use a common Service UUID: `fa87c0d0-afac-11de-8a39-0800200c9a66`
- Both platforms use a common Characteristic UUID: `34B1CF4D-1069-4AD6-89B6-E161D79BE4D8`

These shared identifiers ensure that devices can discover and communicate with each other across platforms.

## API Reference

### Methods

- `initialize(options: { serviceId: string, serviceUUIDString?: string }): Promise<void>`
- `setStrategy(options: { strategy: string }): Promise<void>`
- `startAdvertising(options: { displayName?: string }): Promise<void>`
- `stopAdvertising(): Promise<void>`
- `startDiscovery(): Promise<void>`
- `stopDiscovery(): Promise<void>`
- `connect(options: { endpointId: string, displayName?: string }): Promise<void>`
- `acceptConnection(options: { endpointId: string }): Promise<void>`
- `rejectConnection(options: { endpointId: string }): Promise<void>`
- `disconnectFromEndpoint(options: { endpointId: string }): Promise<void>`
- `disconnect(): Promise<void>`
- `sendMessage(options: { endpointId: string, data: string }): Promise<void>`

### Events

- `connectionRequested`: Fired when a connection request is received
- `connectionResult`: Fired when a connection attempt completes
- `endpointFound`: Fired when a new endpoint is discovered
- `endpointLost`: Fired when an endpoint is lost
- `message`: Fired when a message is received
- `payloadTransferUpdate`: Fired during payload transfer

### Event Types

```typescript
interface ConnectionRequestEvent {
  endpointId: string;
  endpointName: string;
  authenticationToken: string;
  isIncomingConnection: boolean;
}

interface ConnectionResultEvent {
  endpointId: string;
  status: number; // 0 = success, -1 = error
}

interface EndpointFoundEvent {
  endpointId: string;
  endpointName: string;
  serviceId: string;
}

interface EndpointLostEvent {
  endpointId: string;
}

interface MessageReceivedEvent {
  endpointId: string;
  data: string;
}

interface PayloadTransferUpdateEvent {
  endpointId: string;
  bytesTransferred: number;
  totalBytes: number;
  status: number; // 2 = in progress, 3 = completed
}
```

## Inicialización del plugin

```typescript
import { NearbyMultipeer } from '@squareetlabs/capacitor-nearby-multipeer';

await NearbyMultipeer.initialize({
  serviceId: 'mi-servicio',
  serviceUUIDString: 'fa87c0d0-afac-11de-8a39-0800200c9a66' // Opcional, UUID BLE personalizado
});
```

- `serviceId`: Identificador lógico del servicio (obligatorio)
- `serviceUUIDString`: UUID BLE que se usará para el advertising y escaneo (opcional, por defecto: `fa87c0d0-afac-11de-8a39-0800200c9a66`)

Este UUID debe ser el mismo en **Android** e **iOS** para que ambos sistemas puedan descubrirse mutuamente por BLE.
