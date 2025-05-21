# Capacitor Nearby Multipeer Example

This is a demo application for the `@squareetlabs/capacitor-nearby-multipeer` plugin, showing how to implement peer-to-peer connectivity between iOS and Android devices.

## Features

- Device discovery using Bluetooth LE
- Establishing connections between devices
- Sending and receiving messages
- Proper handling of Bluetooth permissions on Android 12+

## Running this example

To run the provided example, follow these steps:

1. Install dependencies:

```bash
npm install
```

2. Build the web assets:

```bash
npm run build
```

3. Add iOS and/or Android platforms:

```bash
npx cap add ios
npx cap add android
```

4. Configure permissions:

- **Android**: Make sure the required permissions are added to `android/app/src/main/AndroidManifest.xml`
- **iOS**: Add Bluetooth usage descriptions to `ios/App/App/Info.plist`

5. Build and run on a device:

```bash
# For iOS
npx cap run ios

# For Android
npx cap run android
```

## Testing

To properly test the functionality:
1. Run the app on two different devices
2. On one device, tap "Request Permissions" (for Android) and then "Initialize"
3. Tap "Start Advertising" on one device and "Start Discovery" on the other
4. When the devices discover each other, tap on the found device to connect
5. Once connected, you can send messages between devices
6. Tap "Stop All" to disconnect and stop advertising/discovery

## Important Notes

- Testing on real devices is required for Bluetooth functionality
- Both devices must have Bluetooth enabled
- For Android 12+ (API 31+), you'll need to grant Bluetooth permissions at runtime
