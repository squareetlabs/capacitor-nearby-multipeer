package com.squareetlabs.capacitor.nearbymultipeer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.ScanRecord;
import android.os.ParcelUuid;

import androidx.annotation.RequiresPermission;

public class NearbyMultipeer {
    private static final String TAG = "NearbyMultipeer";

    private ConnectionsClient connectionsClient;
    private String serviceId;
    private Strategy strategy = Strategy.P2P_STAR;

    // Callbacks para las interacciones de Nearby
    private ConnectionLifecycleCallback connectionLifecycleCallback;
    private EndpointDiscoveryCallback endpointDiscoveryCallback;
    private PayloadCallback payloadCallback;

    // Bluetooth related fields
    private UUID serviceUUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("34B1CF4D-1069-4AD6-89B6-E161D79BE4D8");
    private BluetoothAdapter bluetoothAdapter;
    private Context context;

    // Permission check helper
    private boolean hasBluetoothPermissions() {
        if (context == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12+ (API 31+), check BLUETOOTH_CONNECT permission
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } else {
            // For older versions, check BLUETOOTH permission
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    // Permission check helper for scanning
    private boolean hasBluetoothScanPermissions() {
        if (context == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12+ (API 31+), check BLUETOOTH_SCAN permission
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } else {
            // For older versions, check BLUETOOTH permission
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }
    private boolean isAdvertising = false;
    private boolean isDiscovering = false;
    private boolean isConnected = false;
    private final Map<String, BluetoothDevice> discoveredDevices = new HashMap<>();
    private final Map<String, BluetoothSocket> connectedSockets = new HashMap<>();
    private BluetoothServerSocket serverSocket;
    private AcceptThread acceptThread;
    private final Map<String, ConnectThread> connectThreads = new HashMap<>();
    private final Map<String, ConnectedThread> connectedThreads = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // BLE variables
    private BluetoothLeScanner bleScanner;
    private ScanCallback bleScanCallback;
    private final Map<String, BluetoothDevice> bleDevices = new HashMap<>();
    private BluetoothLeAdvertiser bleAdvertiser;
    private AdvertiseCallback bleAdvertiseCallback;

    public NearbyMultipeer() {
        // Constructor por defecto
    }

    /**
     * Formatea un UUID BLE a 128 bits estándar.
     * Acepta formatos cortos, hexadecimales, sin guiones, etc.
     * Lanza IllegalArgumentException si el UUID no es válido.
     */
    public static String formatBleUuid(String uuid) {
        if (uuid == null) throw new IllegalArgumentException("UUID nulo");
        uuid = uuid.trim().toLowerCase();
        if (uuid.startsWith("0x")) {
            uuid = uuid.substring(2);
        }
        if (uuid.length() < 4) {
            throw new IllegalArgumentException("UUID inválido");
        }
        if (uuid.length() <= 8) {
            uuid = String.format("%8s", uuid).replace(' ', '0') + "-0000-1000-8000-00805f9b34fb";
        }
        if (!uuid.contains("-")) {
            if (uuid.length() != 32) throw new IllegalArgumentException("UUID inválido");
            uuid = uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20, 32);
        }
        String[] groups = uuid.split("-");
        if (groups.length != 5 || groups[0].length() != 8 || groups[1].length() != 4 || groups[2].length() != 4 || groups[3].length() != 4 || groups[4].length() != 12) {
            throw new IllegalArgumentException("UUID inválido");
        }
        for (String g : groups) {
            if (!g.matches("[0-9a-f]+")) throw new IllegalArgumentException("UUID inválido");
        }
        return uuid;
    }

    public void initialize(Context context, String serviceId,
                          ConnectionLifecycleCallback connectionCallback,
                          EndpointDiscoveryCallback discoveryCallback,
                          PayloadCallback payloadCallback,
                          String serviceUUIDString) {
        Log.d(TAG, "[initialize] context=" + context + ", serviceId=" + serviceId + ", connectionCallback=" + connectionCallback + ", discoveryCallback=" + discoveryCallback + ", payloadCallback=" + payloadCallback + ", serviceUUIDString=" + serviceUUIDString);
        this.context = context;
        this.serviceId = serviceId;
        this.connectionLifecycleCallback = connectionCallback;
        this.endpointDiscoveryCallback = discoveryCallback;
        this.payloadCallback = payloadCallback;
        this.connectionsClient = Nearby.getConnectionsClient(context);
        if (serviceUUIDString != null && !serviceUUIDString.isEmpty()) {
            try {
                String formatted = formatBleUuid(serviceUUIDString);
                this.serviceUUID = java.util.UUID.fromString(formatted);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "UUID de servicio inválido, usando el valor por defecto", e);
                this.serviceUUID = java.util.UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
            }
        } else {
            this.serviceUUID = java.util.UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
        }
        Log.i(TAG, "NearbyMultipeer inicializado con serviceId: " + serviceId + ", serviceUUID: " + this.serviceUUID);

        // Initialize Bluetooth
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(bluetoothReceiver, filter);

        Log.i(TAG, "NearbyMultipeer inicializado con serviceId: " + serviceId);
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    String deviceName = null;
                    String deviceAddress = null;

                    try {
                        if (hasBluetoothPermissions()) {
                            deviceName = device.getName();
                            deviceAddress = device.getAddress();
                        } else {
                            Log.w(TAG, "No se tienen permisos para obtener información del dispositivo Bluetooth");
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Error de permisos al obtener información del dispositivo Bluetooth", e);
                    }

                    if (deviceName != null && deviceName.startsWith("iOS_")) {
                        // This is an iOS device (we'll prefix iOS devices with "iOS_")
                        Log.i(TAG, "Found iOS device: " + deviceName);
                        discoveredDevices.put(deviceAddress, device);

                        // Create final copies for use in lambda
                        final String finalDeviceAddress = deviceAddress;
                        final String finalDeviceName = deviceName;

                        // Notify the discovery callback
                        mainHandler.post(() -> {
                            if (endpointDiscoveryCallback != null) {
                                // Create a fake DiscoveredEndpointInfo for the iOS device
                                assert finalDeviceAddress != null;
                                endpointDiscoveryCallback.onEndpointFound(
                                    finalDeviceAddress,
                                    new DiscoveredEndpointInfo(finalDeviceName, serviceId)
                                );
                            }
                        });
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // Discovery finished, restart if still discovering
                if (isDiscovering && bluetoothAdapter != null) {
                    try {
                        if (hasBluetoothScanPermissions()) {
                            bluetoothAdapter.startDiscovery();
                        } else {
                            Log.w(TAG, "No se tienen permisos para iniciar Bluetooth discovery");
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Error de permisos al iniciar Bluetooth discovery", e);
                    }
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    Log.w(TAG, "Bluetooth turned off");
                } else if (state == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "Bluetooth turned on");
                    // Restart advertising or discovery if needed
                    if (isAdvertising) {
                        // Verificar permisos antes de llamar a startBluetoothAdvertising
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) 
                                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                startBluetoothAdvertising();
                            } else {
                                Log.w(TAG, "No se tienen permisos para reiniciar Bluetooth advertising");
                            }
                        } else {
                            startBluetoothAdvertising();
                        }
                    }
                    if (isDiscovering) {
                        // Verificar permisos antes de llamar a startBluetoothDiscovery
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) 
                                    == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) 
                                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                startBluetoothDiscovery();
                            } else {
                                Log.w(TAG, "No se tienen permisos para reiniciar Bluetooth discovery");
                            }
                        } else {
                            startBluetoothDiscovery();
                        }
                    }
                }
            }
        }
    };

    public void setStrategy(Strategy newStrategy) {
        Log.d(TAG, "[setStrategy] newStrategy=" + newStrategy);
        this.strategy = newStrategy;
        Log.i(TAG, "Estrategia cambiada a: " + strategy.toString());
    }

    public String echo(String value) {
        Log.d(TAG, "[echo] value=" + value);
        Log.i("Echo", value);
        return value;
    }

    public void startAdvertising(String displayName, OnResultListener listener) {
        Log.d(TAG, "[startAdvertising] displayName=" + displayName + ", listener=" + listener);
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }

        Log.i(TAG, "Iniciando advertising como: " + displayName);

        // Start advertising with Nearby for Android devices
        connectionsClient.startAdvertising(
                displayName,
                serviceId,
                connectionLifecycleCallback,
                new AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener(unused -> {
            Log.i(TAG, "Nearby advertising iniciado con éxito");

            // Also start Bluetooth advertising for iOS devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startBluetoothAdvertising();
                } else {
                    Log.w(TAG, "No se tienen permisos para iniciar Bluetooth advertising");
                }
            } else {
                startBluetoothAdvertising();
            }

            isAdvertising = true;
            listener.onSuccess();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al iniciar Nearby advertising", e);

            // Try Bluetooth advertising as fallback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startBluetoothAdvertising();
                } else {
                    Log.w(TAG, "No se tienen permisos para iniciar Bluetooth advertising");
                }
            } else {
                startBluetoothAdvertising();
            }

            isAdvertising = true;
            listener.onSuccess();
        });
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_ADVERTISE")
    private void startBluetoothAdvertising() {
        Log.d(TAG, "[startBluetoothAdvertising] bluetoothAdapter=" + bluetoothAdapter);
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth no disponible o no activado");
            return;
        }

        // BLE Advertising
        bleAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (bleAdvertiser == null) {
            Log.e(TAG, "Este dispositivo no soporta BLE Advertising");
            return;
        }

        // Configuración optimizada para máxima visibilidad
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0) // No timeout para mantener la conexión activa
                .build();

        // Datos de fabricante personalizados para ser reconocible por iOS
        byte[] manufacturerData = new byte[] { 
            // Magic bytes para identificar nuestro plugin - MUY IMPORTANTE PARA DETECCIÓN
            0x4E, 0x4D, 0x50, // "NMP" en ASCII (NearbyMultiPeer)
            // Versión del protocolo
            0x01,
            // Tipo de dispositivo (0x01 = Android)
            0x01
        };
        
        // Añadir el nombre del dispositivo a los datos del fabricante
        String deviceName = "Android_" + bluetoothAdapter.getName();
        try {
            // Convertir nombre a bytes y añadirlo a los datos del fabricante
            byte[] nameBytes = deviceName.getBytes("UTF-8");
            byte[] combinedData = new byte[manufacturerData.length + nameBytes.length];
            System.arraycopy(manufacturerData, 0, combinedData, 0, manufacturerData.length);
            System.arraycopy(nameBytes, 0, combinedData, manufacturerData.length, nameBytes.length);
            
            // Usar los datos combinados
            manufacturerData = combinedData;
            BleLogger.info("Nombre de dispositivo añadido a datos del fabricante: " + deviceName);
            BleLogger.logHexData("Manufacturer Data", manufacturerData);
        } catch (Exception e) {
            Log.e(TAG, "Error al añadir nombre del dispositivo a datos del fabricante", e);
            return;
        }
        
        // Crear el AdvertiseData
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(serviceUUID))
                .addManufacturerData(0x0000, manufacturerData)
                .build();

        // Iniciar el advertising
        bleAdvertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.i(TAG, "Advertising iniciado con éxito");
                BleLogger.info("Advertising iniciado con éxito");
                BleLogger.info("Configuración: " + getModeString(settingsInEffect.getMode()) + ", " + getTxPowerString(settingsInEffect.getTxPowerLevel()));
                BleLogger.info("Datos de manufacturer: " + BleLogger.bytesToHex(manufacturerData));
                BleLogger.info("UUID de servicio: " + serviceUUID.toString());
            }

            @Override
            public void onStartFailure(int errorCode) {
                String errorString = getAdvertiseErrorString(errorCode);
                Log.e(TAG, "Error al iniciar advertising: " + errorString);
                BleLogger.error("Error al iniciar advertising: " + errorString);
                
                // Intentar reiniciar después de un error
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Log.i(TAG, "Reintentando iniciar advertising...");
                    startBluetoothAdvertising();
                }, 5000); // Reintentar después de 5 segundos
            }
        };
                // Bytes importantes que ayudan en la detección
                0x4E, 0x4D, 0x50, // "NMP" magic bytes
                0x01, // Versión
                0x01, // Android
            };
            
            // Añadir nombre del dispositivo a los datos de servicio
            try {
                byte[] enhancedServiceData = new byte[serviceData.length + nameBytes.length];
                System.arraycopy(serviceData, 0, enhancedServiceData, 0, serviceData.length);
                System.arraycopy(nameBytes, 0, enhancedServiceData, serviceData.length, nameBytes.length);
                serviceData = enhancedServiceData;
            } catch (Exception e) {
                BleLogger.error("Error al añadir nombre a datos de servicio", e);
            }
            
            // Formato 3: Usar solo serviceData y serviceUUID (más simple)
            AdvertiseData.Builder dataBuilder3 = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(new ParcelUuid(serviceUUID))
                    .addServiceData(new ParcelUuid(serviceUUID), serviceData);
            
            // Usar alternación de formatos de advertising para mejorar compatibilidad
            // Iniciar con el formato 3 que parece ser más efectivo
            AdvertiseData data = dataBuilder3.build();
            
            // Datos en respuesta de escaneo - para proveer información adicional
            AdvertiseData scanResponse = new AdvertiseData.Builder()
                    .setIncludeTxPowerLevel(true)
                    .addServiceUuid(new ParcelUuid(CHARACTERISTIC_UUID))
                    .build();

            BleLogger.info("Iniciando BLE advertising con UUID: " + serviceUUID);
            
            // Callback para manejar éxito/fracaso del advertising
            bleAdvertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    BleLogger.info("✅ BLE advertising iniciado correctamente");
                    BleLogger.info("Modo: " + getModeString(settingsInEffect.getMode()));
                    BleLogger.info("Potencia: " + getTxPowerString(settingsInEffect.getTxPowerLevel()));
                    
                    // Programar cambio de formato de advertising después de 5 segundos
                    // para que iOS tenga más oportunidades de detectar el dispositivo
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isAdvertising && bleAdvertiser != null) {
                            try {
                                BleLogger.info("🔄 Cambiando a formato de advertising alternativo");
                                bleAdvertiser.stopAdvertising(bleAdvertiseCallback);
                                bleAdvertiser.startAdvertising(settings, dataBuilder1.build(), scanResponse, bleAdvertiseCallback);
                            } catch (Exception e) {
                                BleLogger.error("Error al cambiar formato de advertising", e);
                            }
                        }
                    }, 5000); // 5 segundos
                    
                    // Programar otro cambio después de 10 segundos
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isAdvertising && bleAdvertiser != null) {
                            try {
                                BleLogger.info("🔄 Cambiando a formato de advertising final");
                                bleAdvertiser.stopAdvertising(bleAdvertiseCallback);
                                bleAdvertiser.startAdvertising(settings, dataBuilder2.build(), scanResponse, bleAdvertiseCallback);
                            } catch (Exception e) {
                                BleLogger.error("Error al cambiar formato de advertising", e);
                            }
                        }
                    }, 10000); // 10 segundos
                }

                @Override
                public void onStartFailure(int errorCode) {
                    BleLogger.error("❌ Error al iniciar BLE advertising: " + getAdvertiseErrorString(errorCode));
                    
                    // Si falla, intentar con otro formato
                    try {
                        BleLogger.info("🔄 Intentando con formato alternativo tras error");
                        bleAdvertiser.startAdvertising(settings, dataBuilder1.build(), scanResponse, this);
                    } catch (Exception e) {
                        BleLogger.error("Error al iniciar advertising alternativo", e);
                    }
                }
            };

            // Iniciar advertising con el formato inicial
            try {
                bleAdvertiser.startAdvertising(settings, data, scanResponse, bleAdvertiseCallback);
            } catch (Exception e) {
                BleLogger.error("Error al iniciar advertising principal", e);
                // Intentar con formato alternativo
                try {
                    bleAdvertiser.startAdvertising(settings, dataBuilder1.build(), scanResponse, bleAdvertiseCallback);
                } catch (Exception e2) {
                    BleLogger.error("Error al iniciar advertising alternativo", e2);
                }
            }
        }

        // Advertising clásico para compatibilidad adicional
        stopBluetoothAdvertisingClassic();
        acceptThread = new AcceptThread();
        acceptThread.start();
        Log.i(TAG, "Bluetooth advertising clásico iniciado");
    }

    private void stopBluetoothAdvertising() {
        Log.d(TAG, "[stopBluetoothAdvertising] acceptThread=" + acceptThread + ", serverSocket=" + serverSocket);
        // Parar BLE Advertising
        if (bleAdvertiser != null && bleAdvertiseCallback != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) 
                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        bleAdvertiser.stopAdvertising(bleAdvertiseCallback);
                        Log.i(TAG, "BLE advertising detenido");
                    } else {
                        Log.w(TAG, "No se tienen permisos para detener BLE advertising");
                    }
                } else {
                    bleAdvertiser.stopAdvertising(bleAdvertiseCallback);
                    Log.i(TAG, "BLE advertising detenido");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Error de permisos al detener BLE advertising", e);
            }
        }
        // Parar advertising clásico
        stopBluetoothAdvertisingClassic();
    }

    private void stopBluetoothAdvertisingClassic() {
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        if (serverSocket != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) 
                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        serverSocket.close();
                    } else {
                        Log.w(TAG, "No se tienen permisos para cerrar el servidor Bluetooth");
                    }
                } else {
                    serverSocket.close();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Error de permisos al cerrar el servidor Bluetooth", e);
            } catch (IOException e) {
                Log.e(TAG, "Error al cerrar el servidor Bluetooth", e);
            }
            serverSocket = null;
        }
    }

    public void stopAdvertising() {
        Log.d(TAG, "[stopAdvertising] isAdvertising=" + isAdvertising);
        if (connectionsClient != null) {
            connectionsClient.stopAdvertising();
            Log.i(TAG, "Nearby advertising detenido");
        }

        stopBluetoothAdvertising();
        isAdvertising = false;
        Log.i(TAG, "Advertising detenido");
    }

    public void startDiscovery(OnResultListener listener) {
        Log.d(TAG, "[startDiscovery] listener=" + listener);
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }

        Log.i(TAG, "Iniciando discovery para serviceId: " + serviceId);

        // Start discovery with Nearby for Android devices
        connectionsClient.startDiscovery(
                serviceId,
                endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener(unused -> {
            Log.i(TAG, "Nearby discovery iniciado con éxito");

            // Also start Bluetooth discovery for iOS devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startBluetoothDiscovery();
                } else {
                    Log.w(TAG, "No se tienen permisos para iniciar Bluetooth discovery");
                }
            } else {
                startBluetoothDiscovery();
            }

            isDiscovering = true;
            listener.onSuccess();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al iniciar Nearby discovery", e);

            // Try Bluetooth discovery as fallback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startBluetoothDiscovery();
                } else {
                    Log.w(TAG, "No se tienen permisos para iniciar Bluetooth discovery");
                }
            } else {
                startBluetoothDiscovery();
            }

            isDiscovering = true;
            listener.onSuccess();
        });
    }

    @RequiresPermission(allOf = {"android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"})
    private void startBluetoothDiscovery() {
        Log.d(TAG, "[startBluetoothDiscovery] bluetoothAdapter=" + bluetoothAdapter);
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth no disponible");
            return;
        }

        // Check permissions for Bluetooth operations
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "No se tienen permisos para usar Bluetooth");
            return;
        }

        try {
            // Make sure Bluetooth is enabled
            if (!bluetoothAdapter.isEnabled()) {
                Log.w(TAG, "Bluetooth no está activado");
                return;
            }

            // Clear previously discovered devices
            discoveredDevices.clear();
            
            // Iniciar escaneo BLE específico
            startBleScanning();

            // Check scan permissions
            if (!hasBluetoothScanPermissions()) {
                Log.e(TAG, "No se tienen permisos para escanear dispositivos Bluetooth");
                return;
            }

            // Check if we're already discovering
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }

            // Start discovery
            if (hasBluetoothScanPermissions()) {
                bluetoothAdapter.startDiscovery();
                Log.i(TAG, "Bluetooth discovery clásico iniciado");
            } else {
                Log.e(TAG, "No se tienen permisos para iniciar Bluetooth discovery");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error de permisos en operaciones Bluetooth", e);
        }
    }

    /**
     * Inicia el escaneo específico BLE para encontrar dispositivos iOS y Android
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_SCAN")
    private void startBleScanning() {
        BleLogger.info("Iniciando escaneo BLE...");
        bleDevices.clear();
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            BleLogger.error("Bluetooth no disponible o no activado");
            return;
        }

        // Obtener scanner BLE
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            BleLogger.error("Este dispositivo no soporta BLE scanning");
            return;
        }

        // Filtros para escanear específicamente nuestro servicio
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter serviceFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(serviceUUID))
                .build();
        filters.add(serviceFilter);
        
        // También podemos buscar dispositivos por los datos de fabricante personalizados
        byte[] manufacturerDataMask = new byte[]{ (byte)0xFF, (byte)0xFF, (byte)0xFF }; // Solo comparar los primeros 3 bytes ("NMP")
        byte[] manufacturerDataFilter = new byte[]{ 0x4E, 0x4D, 0x50 }; // "NMP" en ASCII
        
        ScanFilter manufacturerFilter = new ScanFilter.Builder()
                .setManufacturerData(0xFF, manufacturerDataFilter, manufacturerDataMask)
                .build();
        filters.add(manufacturerFilter);
        
        // Configuración optimizada para descubrimiento rápido
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0) // Reporte inmediato
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .build();

        // Detener escaneo anterior si existe
        if (bleScanCallback != null) {
            try {
                bleScanner.stopScan(bleScanCallback);
            } catch (Exception e) {
                BleLogger.error("Error al detener escaneo BLE previo", e);
            }
        }

        BleLogger.info("Iniciando escaneo BLE con UUID: " + serviceUUID);
        
        bleScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (device == null) return;
                
                String deviceName;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) 
                                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            deviceName = device.getName();
                        } else {
                            deviceName = "Unknown";
                        }
                    } else {
                        deviceName = device.getName();
                    }
                } catch (SecurityException e) {
                    deviceName = "Unknown";
                    BleLogger.error("Error de permisos al obtener nombre del dispositivo", e);
                }
                
                if (deviceName == null) deviceName = "Unknown";
                
                String deviceAddress = device.getAddress();
                int rssi = result.getRssi();
                
                // Para depuración, mostrar todos los dispositivos encontrados
                BleLogger.debug("Dispositivo BLE encontrado: " + deviceName + " (" + deviceAddress + ") RSSI: " + rssi);
                
                // Detectar si es un dispositivo iOS por el nombre o datos de fabricante
                boolean isIosDevice = false;
                ScanRecord scanRecord = result.getScanRecord();
                
                if (scanRecord != null) {
                    byte[] manufacturerData = scanRecord.getManufacturerSpecificData(0xFF);
                    if (manufacturerData != null && manufacturerData.length >= 5) {
                        // Verificar magic bytes "NMP"
                        if (manufacturerData[0] == 0x4E && manufacturerData[1] == 0x4D && manufacturerData[2] == 0x50) {
                            // Verificar tipo de dispositivo (0x02 = iOS)
                            isIosDevice = manufacturerData[4] == 0x02;
                            BleLogger.info("Datos de fabricante detectados, es dispositivo iOS: " + isIosDevice);
                            BleLogger.logHexData("Manufacturer Data", manufacturerData);
                        }
                    }
                }
                
                if (deviceName.startsWith("iOS_") || isIosDevice) {
                    // Es un dispositivo iOS
                    String serviceId = "iOS_iPhone";
                    
                    // Para depuración, mostrar los datos detallados
                    BleLogger.info("Dispositivo iOS BLE encontrado: " + deviceName);
                    if (scanRecord != null) {
                        BleLogger.debug("Datos de advertising: " + scanRecord);
                        if (scanRecord.getServiceUuids() != null) {
                            for (ParcelUuid uuid : scanRecord.getServiceUuids()) {
                                BleLogger.debug("Servicio anunciado: " + uuid.toString());
                            }
                        }
                    }
                    
                    // Si ya habíamos detectado este dispositivo, no notificar de nuevo
                    if (!bleDevices.containsKey(deviceAddress)) {
                        bleDevices.put(deviceAddress, device);
                        discoveredDevices.put(deviceAddress, device); // También guardar en la lista general
                        
                        // Notificar a través del callback de Nearby
                        if (endpointDiscoveryCallback != null) {
                            String finalDeviceName = deviceName;
                            mainHandler.post(() -> endpointDiscoveryCallback.onEndpointFound(deviceAddress, new DiscoveredEndpointInfo(serviceId, finalDeviceName)));
                        }
                    }
                }
                else if (deviceName.startsWith("Android_") || (scanRecord != null && scanRecord.getServiceUuids() != null && 
                        scanRecord.getServiceUuids().contains(new ParcelUuid(serviceUUID)))) {
                    // Es un dispositivo Android con nuestro servicio
                    BleLogger.info("Dispositivo Android BLE encontrado: " + deviceName);
                    String serviceId = "Android_Device";
                    
                    // Si ya habíamos detectado este dispositivo, no notificar de nuevo
                    if (!bleDevices.containsKey(deviceAddress)) {
                        bleDevices.put(deviceAddress, device);
                        discoveredDevices.put(deviceAddress, device); // También guardar en la lista general
                        
                        // Notificar a través del callback de Nearby
                        if (endpointDiscoveryCallback != null) {
                            String finalDeviceName = deviceName;
                            mainHandler.post(() -> endpointDiscoveryCallback.onEndpointFound(deviceAddress, new DiscoveredEndpointInfo(serviceId, finalDeviceName)));
                        }
                    }
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                BleLogger.info("Batch scan results: " + results.size() + " dispositivos");
                for (ScanResult result : results) {
                    onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                BleLogger.error("BLE scan failed: " + getScanErrorString(errorCode));
            }
        };

        try {
            if (hasBluetoothScanPermissions()) {
                bleScanner.startScan(filters, scanSettings, bleScanCallback);
                BleLogger.info("Escaneo BLE iniciado con éxito");
            } else {
                BleLogger.error("No se tienen permisos para escanear BLE");
                throw new SecurityException("No se tienen permisos BLUETOOTH_SCAN");
            }
        } catch (SecurityException e) {
            BleLogger.error("Error de permisos al iniciar escaneo BLE", e);
        } catch (Exception e) {
            BleLogger.error("Error al iniciar escaneo BLE", e);
        }
    }
    
    // Detener escaneo BLE específicamente
    private void stopBleScanning() {
        if (bluetoothAdapter == null || bleScanner == null) return;
        
        if (bleScanCallback != null) {
            try {
                if (hasBluetoothScanPermissions()) {
                    bleScanner.stopScan(bleScanCallback);
                    BleLogger.info("Escaneo BLE detenido");
                } else {
                    BleLogger.error("No se tienen permisos para detener escaneo BLE");
                }
            } catch (SecurityException e) {
                BleLogger.error("Error de permisos al detener escaneo BLE", e);
            } catch (Exception e) {
                BleLogger.error("Error al detener escaneo BLE", e);
            }
        }
    }

    private void stopBluetoothDiscovery() {
        Log.d(TAG, "[stopBluetoothDiscovery] bluetoothAdapter=" + bluetoothAdapter);
        if (bluetoothAdapter == null) return;

        // Detener escaneo BLE
        stopBleScanning();

        // Check permissions
        if (!hasBluetoothScanPermissions()) {
            Log.e(TAG, "No se tienen permisos para controlar Bluetooth discovery");
            return;
        }

        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
                Log.i(TAG, "Bluetooth discovery detenido");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error de permisos al detener Bluetooth discovery", e);
        }
    }

    public void stopDiscovery() {
        Log.d(TAG, "[stopDiscovery] isDiscovering=" + isDiscovering);
        if (connectionsClient != null) {
            connectionsClient.stopDiscovery();
            Log.i(TAG, "Nearby discovery detenido");
        }

        stopBluetoothDiscovery();
        isDiscovering = false;
        Log.i(TAG, "Discovery detenido");
    }

    public void requestConnection(String displayName, String endpointId, OnResultListener listener) {
        Log.d(TAG, "[requestConnection] displayName=" + displayName + ", endpointId=" + endpointId + ", listener=" + listener);
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }

        // Check if this is a Bluetooth device (iOS)
        if (discoveredDevices.containsKey(endpointId)) {
            // This is an iOS device, connect via Bluetooth
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    connectToBluetoothDevice(endpointId, listener);
                } else {
                    Log.e(TAG, "No se tienen permisos para conectar con dispositivos Bluetooth");
                    listener.onFailure("No se tienen permisos para conectar con dispositivos Bluetooth");
                }
            } else {
                connectToBluetoothDevice(endpointId, listener);
            }
        } else {
            // This is an Android device, connect via Nearby
            Log.i(TAG, "Solicitando conexión a endpoint Nearby: " + endpointId);
            try {
                connectionsClient.requestConnection(
                        displayName,
                        endpointId,
                        connectionLifecycleCallback
                ).addOnSuccessListener(unused -> {
                    Log.i(TAG, "Solicitud de conexión Nearby enviada con éxito");

                    // Stop advertising once connected
                    if (isAdvertising) {
                        stopAdvertising();
                    }

                    listener.onSuccess();
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Error al solicitar conexión Nearby", e);
                    listener.onFailure("Error al solicitar conexión: " + e.getMessage());
                });
            } catch (SecurityException e) {
                Log.e(TAG, "Error de permisos al solicitar conexión Nearby", e);
                listener.onFailure("Error de permisos: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error al solicitar conexión Nearby", e);
                listener.onFailure("Error: " + e.getMessage());
            }
        }
    }

    @RequiresPermission(allOf = {"android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"})
    private void connectToBluetoothDevice(String deviceAddress, OnResultListener listener) {
        Log.d(TAG, "[connectToBluetoothDevice] deviceAddress=" + deviceAddress + ", listener=" + listener);
        BluetoothDevice device = discoveredDevices.get(deviceAddress);
        if (device == null) {
            listener.onFailure("Dispositivo no encontrado: " + deviceAddress);
            return;
        }

        // Check permissions
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "No se tienen permisos para conectar con dispositivos Bluetooth");
            listener.onFailure("No se tienen permisos para conectar con dispositivos Bluetooth");
            return;
        }

        try {
            // Stop discovery before connecting
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error de permisos al cancelar Bluetooth discovery", e);
            listener.onFailure("Error de permisos: " + e.getMessage());
            return;
        }

        // Create and start the connect thread
        ConnectThread connectThread = new ConnectThread(device, deviceAddress);
        connectThreads.put(deviceAddress, connectThread);
        connectThread.start();

        // Stop advertising once connected
        if (isAdvertising) {
            stopAdvertising();
        }

        Log.i(TAG, "Iniciando conexión Bluetooth a: " + deviceAddress);
        listener.onSuccess();
    }

    public void acceptConnection(String endpointId, OnResultListener listener) {
        Log.d(TAG, "[acceptConnection] endpointId=" + endpointId + ", listener=" + listener);
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }

        // Check if this is a Bluetooth connection (iOS device)
        if (connectedSockets.containsKey(endpointId)) {
            // For Bluetooth, the connection is already established at this point
            // We just need to start the connected thread to handle data transfer
            BluetoothSocket socket = connectedSockets.get(endpointId);
            if (socket != null) {
                ConnectedThread connectedThread = new ConnectedThread(socket, endpointId);
                connectedThreads.put(endpointId, connectedThread);
                connectedThread.start();

                // Stop advertising once connected
                if (isAdvertising) {
                    stopAdvertising();
                }

                isConnected = true;
                Log.i(TAG, "Conexión Bluetooth aceptada con éxito");
                listener.onSuccess();

                // Notify connection result
                mainHandler.post(() -> {
                    if (connectionLifecycleCallback != null) {
                        connectionLifecycleCallback.onConnectionResult(
                            endpointId,
                            new com.google.android.gms.nearby.connection.ConnectionResolution(
                                com.google.android.gms.common.api.Status.RESULT_SUCCESS
                            )
                        );
                    }
                });
            } else {
                listener.onFailure("Socket no disponible para: " + endpointId);
            }
        } else {
            // This is a Nearby connection (Android device)
            Log.i(TAG, "Aceptando conexión de endpoint Nearby: " + endpointId);
            try {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                        .addOnSuccessListener(unused -> {
                            Log.i(TAG, "Conexión Nearby aceptada con éxito");

                            // Stop advertising once connected
                            if (isAdvertising) {
                                stopAdvertising();
                            }

                            isConnected = true;
                            listener.onSuccess();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error al aceptar conexión Nearby", e);
                            listener.onFailure("Error al aceptar conexión: " + e.getMessage());
                        });
            } catch (SecurityException e) {
                Log.e(TAG, "Error de permisos al aceptar conexión Nearby", e);
                listener.onFailure("Error de permisos: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error al aceptar conexión Nearby", e);
                listener.onFailure("Error: " + e.getMessage());
            }
        }
    }

    public void rejectConnection(String endpointId, OnResultListener listener) {
        Log.d(TAG, "[rejectConnection] endpointId=" + endpointId + ", listener=" + listener);
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }

        // Check if this is a Bluetooth connection (iOS device)
        if (connectedSockets.containsKey(endpointId)) {
            // Close the Bluetooth socket
            BluetoothSocket socket = connectedSockets.remove(endpointId);
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error al cerrar socket Bluetooth", e);
                }
            }

            // Remove any connect thread
            ConnectThread connectThread = connectThreads.remove(endpointId);
            if (connectThread != null) {
                connectThread.cancel();
            }

            Log.i(TAG, "Conexión Bluetooth rechazada con éxito");
            listener.onSuccess();
        } else {
            // This is a Nearby connection (Android device)
            Log.i(TAG, "Rechazando conexión de endpoint Nearby: " + endpointId);
            try {
                connectionsClient.rejectConnection(endpointId)
                        .addOnSuccessListener(unused -> {
                            Log.i(TAG, "Conexión Nearby rechazada con éxito");
                            listener.onSuccess();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error al rechazar conexión Nearby", e);
                            listener.onFailure("Error al rechazar conexión: " + e.getMessage());
                        });
            } catch (SecurityException e) {
                Log.e(TAG, "Error de permisos al rechazar conexión Nearby", e);
                listener.onFailure("Error de permisos: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error al rechazar conexión Nearby", e);
                listener.onFailure("Error: " + e.getMessage());
            }
        }
    }

    public void sendMessage(String endpointId, String message, OnResultListener listener) {
        Log.d(TAG, "[sendMessage] endpointId=" + endpointId + ", message=" + message + ", listener=" + listener);
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }

        // Check if this is a Bluetooth connection (iOS device)
        ConnectedThread connectedThread = connectedThreads.get(endpointId);
        if (connectedThread != null) {
            // Send via Bluetooth
            byte[] bytes = message.getBytes();
            connectedThread.write(bytes);
            Log.i(TAG, "Mensaje Bluetooth enviado con éxito");
            listener.onSuccess();
        } else {
            // Send via Nearby
            byte[] bytes = message.getBytes();
            Payload payload = Payload.fromBytes(bytes);

            Log.i(TAG, "Enviando mensaje a endpoint Nearby: " + endpointId);
            try {
                connectionsClient.sendPayload(endpointId, payload)
                        .addOnSuccessListener(unused -> {
                            Log.i(TAG, "Mensaje Nearby enviado con éxito");
                            listener.onSuccess();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error al enviar mensaje Nearby", e);
                            listener.onFailure("Error al enviar mensaje: " + e.getMessage());
                        });
            } catch (SecurityException e) {
                Log.e(TAG, "Error de permisos al enviar mensaje Nearby", e);
                listener.onFailure("Error de permisos: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error al enviar mensaje Nearby", e);
                listener.onFailure("Error: " + e.getMessage());
            }
        }
    }

    public void disconnectFromEndpoint(String endpointId) {
        Log.d(TAG, "[disconnectFromEndpoint] endpointId=" + endpointId);
        // Check if this is a Bluetooth connection (iOS device)
        ConnectedThread connectedThread = connectedThreads.remove(endpointId);
        if (connectedThread != null) {
            // Disconnect Bluetooth
            connectedThread.cancel();

            // Close the socket
            BluetoothSocket socket = connectedSockets.remove(endpointId);
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error al cerrar socket Bluetooth", e);
                }
            }

            // Resume advertising after disconnection
            if (!isAdvertising && !isConnected) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) 
                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        startBluetoothAdvertising();
                        isAdvertising = true;
                    } else {
                        Log.w(TAG, "No se tienen permisos para reiniciar Bluetooth advertising");
                    }
                } else {
                    startBluetoothAdvertising();
                    isAdvertising = true;
                }
            }

            Log.i(TAG, "Desconectado del endpoint Bluetooth: " + endpointId);

            // Notify disconnection
            mainHandler.post(() -> {
                if (connectionLifecycleCallback != null) {
                    connectionLifecycleCallback.onDisconnected(endpointId);
                }
            });
        } else if (connectionsClient != null) {
            // Disconnect Nearby
            connectionsClient.disconnectFromEndpoint(endpointId);

            // Resume advertising after disconnection
            if (!isAdvertising && !isConnected) {
                startAdvertising("AndroidDevice", new OnResultListener() {
                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "Advertising reiniciado después de desconexión");
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e(TAG, "Error al reiniciar advertising: " + error);
                    }
                });
            }

            Log.i(TAG, "Desconectado del endpoint Nearby: " + endpointId);
        }
    }

    public void disconnectFromAllEndpoints() {
        Log.d(TAG, "[disconnectFromAllEndpoints]");
        // Disconnect all Bluetooth connections
        for (ConnectedThread thread : connectedThreads.values()) {
            if (thread != null) {
                thread.cancel();
            }
        }
        connectedThreads.clear();

        // Close all sockets
        for (BluetoothSocket socket : connectedSockets.values()) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error al cerrar socket Bluetooth", e);
                }
            }
        }
        connectedSockets.clear();

        // Disconnect all Nearby connections
        if (connectionsClient != null) {
            connectionsClient.stopAllEndpoints();
        }

        // Resume advertising after disconnection
        if (!isAdvertising && !isConnected) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startBluetoothAdvertising();
                    isAdvertising = true;
                } else {
                    Log.w(TAG, "No se tienen permisos para reiniciar Bluetooth advertising");
                }
            } else {
                startBluetoothAdvertising();
                isAdvertising = true;
            }
        }

        Log.i(TAG, "Desconectado de todos los endpoints");
    }

    public void cleanup() {
        Log.d(TAG, "[cleanup]");
        if (connectionsClient != null) {
            stopAdvertising();
            stopDiscovery();
            disconnectFromAllEndpoints();

            // Unregister the broadcast receiver
            try {
                context.unregisterReceiver(bluetoothReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error al desregistrar el receptor Bluetooth", e);
            }

            Log.i(TAG, "Recursos liberados");
        }
    }

    // Interfaz para manejar resultados asíncronos
    public interface OnResultListener {
        void onSuccess();
        void onFailure(String error);
    }

    // Thread para aceptar conexiones Bluetooth entrantes
    private class AcceptThread extends Thread {
        public AcceptThread() {
            // Check permissions before creating server socket
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "No se tienen permisos para crear un servidor Bluetooth");
                return;
            }

            try {
                // Create a new listening server socket
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("NearbyMultipeer", serviceUUID);
                Log.d(TAG, "Servidor Bluetooth creado");
            } catch (SecurityException e) {
                Log.e(TAG, "Error de permisos al crear el servidor Bluetooth", e);
            } catch (IOException e) {
                Log.e(TAG, "Error al crear el servidor Bluetooth", e);
            }
        }

        public void run() {
            BluetoothSocket socket = null;

            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    Log.d(TAG, "Esperando conexiones Bluetooth entrantes...");
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Error al aceptar conexión Bluetooth", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    // Get the connected device
                    BluetoothDevice device = socket.getRemoteDevice();
                    String deviceAddress = null;
                    String deviceName = null;

                    try {
                        if (hasBluetoothPermissions()) {
                            deviceAddress = device.getAddress();
                            deviceName = device.getName();
                        } else {
                            Log.w(TAG, "No se tienen permisos para obtener información del dispositivo Bluetooth");
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Error de permisos al obtener información del dispositivo Bluetooth", e);
                    }

                    Log.i(TAG, "Conexión Bluetooth entrante de: " + deviceName + " (" + deviceAddress + ")");

                    // Store the socket
                    connectedSockets.put(deviceAddress, socket);

                    // Create final copies for use in lambda
                    final String finalDeviceAddress = deviceAddress;
                    final String finalDeviceName = deviceName;

                    // Notify the connection request
                    mainHandler.post(() -> {
                        if (connectionLifecycleCallback != null) {
                            connectionLifecycleCallback.onConnectionInitiated(
                                finalDeviceAddress,
                                new ConnectionInfo(
                                    finalDeviceName,
                                    "",
                                    true
                                )
                            );
                        }
                    });

                    // Don't need to keep listening if we're only expecting one connection
                    if (isConnected) {
                        break;
                    }
                }
            }
        }

        public void cancel() {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error al cerrar el servidor Bluetooth", e);
            }
        }
    }

    // Thread para conectar a un dispositivo Bluetooth
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final String mmDeviceAddress;

        public ConnectThread(BluetoothDevice device, String deviceAddress) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            mmDeviceAddress = deviceAddress;

            try {
                // Check permissions before creating socket
                if (hasBluetoothPermissions()) {
                    // Get a BluetoothSocket for a connection with the given BluetoothDevice
                    tmp = device.createRfcommSocketToServiceRecord(serviceUUID);
                } else {
                    Log.e(TAG, "No se tienen permisos para crear socket Bluetooth");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Error de permisos al crear socket Bluetooth", e);
            } catch (IOException e) {
                Log.e(TAG, "Error al crear socket Bluetooth", e);
            }

            mmSocket = tmp;
        }

        public void run() {
            // Check permissions before canceling discovery
            if (!hasBluetoothScanPermissions()) {
                Log.e(TAG, "No se tienen permisos para controlar Bluetooth discovery");
            } else {
                try {
                    // Cancel discovery because it otherwise slows down the connection
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Error de permisos al cancelar Bluetooth discovery", e);
                }
            }

            try {
                // Connect to the remote device through the socket
                String deviceName = "Unknown Device";
                try {
                    if (hasBluetoothPermissions()) {
                        deviceName = mmDevice.getName();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Error de permisos al obtener nombre del dispositivo Bluetooth", e);
                }

                Log.d(TAG, "Conectando a dispositivo Bluetooth: " + deviceName);

                // Check Bluetooth permissions before connecting
                if (!hasBluetoothPermissions()) {
                    Log.e(TAG, "No se tienen permisos para conectar con dispositivo Bluetooth");
                    throw new IOException("Bluetooth connection permission denied");
                }

                try {
                    mmSocket.connect();
                } catch (SecurityException e) {
                    Log.e(TAG, "Error de permisos al conectar con dispositivo Bluetooth", e);
                    throw new IOException("Bluetooth connection permission denied", e);
                }

                // Store the socket
                connectedSockets.put(mmDeviceAddress, mmSocket);

                // Notify the connection result
                mainHandler.post(() -> {
                    if (connectionLifecycleCallback != null) {
                        connectionLifecycleCallback.onConnectionResult(
                            mmDeviceAddress,
                            new com.google.android.gms.nearby.connection.ConnectionResolution(
                                com.google.android.gms.common.api.Status.RESULT_SUCCESS
                            )
                        );
                    }
                });

                // Start the connected thread to manage the connection
                ConnectedThread connectedThread = new ConnectedThread(mmSocket, mmDeviceAddress);
                connectedThreads.put(mmDeviceAddress, connectedThread);
                connectedThread.start();

                // Set connected flag
                isConnected = true;

            } catch (IOException connectException) {
                Log.e(TAG, "Error al conectar con dispositivo Bluetooth", connectException);

                // Notify the connection result
                mainHandler.post(() -> {
                    if (connectionLifecycleCallback != null) {
                        connectionLifecycleCallback.onConnectionResult(
                            mmDeviceAddress,
                            new com.google.android.gms.nearby.connection.ConnectionResolution(
                                com.google.android.gms.common.api.Status.RESULT_INTERNAL_ERROR
                            )
                        );
                    }
                });

                // Close the socket
                if (hasBluetoothPermissions()) {
                    try {
                        mmSocket.close();
                    } catch (SecurityException e) {
                        Log.e(TAG, "Error de permisos al cerrar socket Bluetooth", e);
                    } catch (IOException closeException) {
                        Log.e(TAG, "Error al cerrar socket Bluetooth", closeException);
                    }
                } else {
                    Log.w(TAG, "No se tienen permisos para cerrar socket Bluetooth");
                }
            }
        }

        public void cancel() {
            if (hasBluetoothPermissions()) {
                try {
                    mmSocket.close();
                } catch (SecurityException e) {
                    Log.e(TAG, "Error de permisos al cerrar socket Bluetooth", e);
                } catch (IOException e) {
                    Log.e(TAG, "Error al cerrar socket Bluetooth", e);
                }
            } else {
                Log.w(TAG, "No se tienen permisos para cerrar socket Bluetooth");
            }
        }
    }

    // Thread para manejar la comunicación Bluetooth
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final String mmDeviceAddress;

        public ConnectedThread(BluetoothSocket socket, String deviceAddress) {
            mmSocket = socket;
            mmDeviceAddress = deviceAddress;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error al obtener streams Bluetooth", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] mmBuffer = new byte[1024];
            int numBytes;

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                if (!hasBluetoothPermissions()) {
                    Log.w(TAG, "No se tienen permisos para leer datos Bluetooth");
                    break;
                }

                try {
                    // Read from the InputStream
                    numBytes = mmInStream.read(mmBuffer);
                } catch (SecurityException e) {
                    Log.e(TAG, "Error de permisos al leer datos Bluetooth", e);
                    break;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // Convert to string and notify
                String message = new String(mmBuffer, 0, numBytes);
                Log.d(TAG, "Mensaje Bluetooth recibido: " + message);

                // Notify the payload callback
                mainHandler.post(() -> {
                    if (payloadCallback != null) {
                        payloadCallback.onPayloadReceived(
                            mmDeviceAddress,
                            Payload.fromBytes(message.getBytes())
                        );
                    }
                });
            }
        }

        public void write(byte[] bytes) {
            if (!hasBluetoothPermissions()) {
                Log.w(TAG, "No se tienen permisos para escribir datos Bluetooth");
                return;
            }

            try {
                mmOutStream.write(bytes);

                // Notify the payload transfer update
                mainHandler.post(() -> {
                    if (payloadCallback != null) {
                        payloadCallback.onPayloadTransferUpdate(
                            mmDeviceAddress,
                            new PayloadTransferUpdate.Builder()
                                .setPayloadId(0)
                                .setTotalBytes(bytes.length)
                                .setBytesTransferred(bytes.length)
                                .setStatus(PayloadTransferUpdate.Status.SUCCESS)
                                .build()
                        );
                    }
                });

                Log.d(TAG, "Mensaje Bluetooth enviado: " + new String(bytes));
            } catch (SecurityException e) {
                Log.e(TAG, "Error de permisos al escribir datos Bluetooth", e);
                return;
            } catch (IOException e) {
                Log.e(TAG, "Error al enviar datos Bluetooth", e);
            }
        }

        public void cancel() {
            if (hasBluetoothPermissions()) {
                try {
                    mmSocket.close();
                } catch (SecurityException e) {
                    Log.e(TAG, "Error de permisos al cerrar socket Bluetooth", e);
                } catch (IOException e) {
                    Log.e(TAG, "Error al cerrar socket Bluetooth", e);
                }
            } else {
                Log.w(TAG, "No se tienen permisos para cerrar socket Bluetooth");
            }
        }
    }

    // Utilidades para logging detallado
    private String getModeString(int mode) {
        switch (mode) {
            case AdvertiseSettings.ADVERTISE_MODE_LOW_POWER:
                return "ADVERTISE_MODE_LOW_POWER";
            case AdvertiseSettings.ADVERTISE_MODE_BALANCED:
                return "ADVERTISE_MODE_BALANCED";
            case AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY:
                return "ADVERTISE_MODE_LOW_LATENCY";
            default:
                return "UNKNOWN MODE: " + mode;
        }
    }
    
    private String getTxPowerString(int txPowerLevel) {
        switch (txPowerLevel) {
            case AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW:
                return "ADVERTISE_TX_POWER_ULTRA_LOW";
            case AdvertiseSettings.ADVERTISE_TX_POWER_LOW:
                return "ADVERTISE_TX_POWER_LOW";
            case AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM:
                return "ADVERTISE_TX_POWER_MEDIUM";
            case AdvertiseSettings.ADVERTISE_TX_POWER_HIGH:
                return "ADVERTISE_TX_POWER_HIGH";
            default:
                return "UNKNOWN TX POWER: " + txPowerLevel;
        }
    }
    
    private String getAdvertiseErrorString(int errorCode) {
        switch (errorCode) {
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                return "ADVERTISE_FAILED_ALREADY_STARTED";
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "ADVERTISE_FAILED_DATA_TOO_LARGE";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "ADVERTISE_FAILED_FEATURE_UNSUPPORTED";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                return "ADVERTISE_FAILED_INTERNAL_ERROR";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS";
            default:
                return "UNKNOWN ERROR: " + errorCode;
        }
    }

    // Utilidad para convertir códigos de error de escaneo a texto
    private String getScanErrorString(int errorCode) {
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                return "SCAN_FAILED_ALREADY_STARTED";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return "SCAN_FAILED_FEATURE_UNSUPPORTED";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return "SCAN_FAILED_INTERNAL_ERROR";
            default:
                return "UNKNOWN ERROR: " + errorCode;
        }
    }
}
