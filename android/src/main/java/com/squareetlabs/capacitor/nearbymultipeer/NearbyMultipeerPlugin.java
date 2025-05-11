package com.squareetlabs.capacitor.nearbymultipeer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
@SuppressLint("MissingPermission")
@CapacitorPlugin(
    name = "NearbyMultipeer",
    permissions = {
        @Permission(
            strings = {Manifest.permission.ACCESS_COARSE_LOCATION},
            alias = "ACCESS_COARSE_LOCATION"
        ),
        @Permission(
            strings = {Manifest.permission.ACCESS_FINE_LOCATION},
            alias = "ACCESS_FINE_LOCATION"
        ),
        @Permission(
            strings = {Manifest.permission.BLUETOOTH},
            alias = "BLUETOOTH"
        ),
        @Permission(
            strings = {Manifest.permission.BLUETOOTH_ADMIN},
            alias = "BLUETOOTH_ADMIN"
        ),
        @Permission(
            strings = {"android.permission.BLUETOOTH_SCAN"},
            alias = "BLUETOOTH_SCAN"
        ),
        @Permission(
            strings = {"android.permission.BLUETOOTH_CONNECT"},
            alias = "BLUETOOTH_CONNECT"
        ),
        @Permission(
            strings = {"android.permission.BLUETOOTH_ADVERTISE"},
            alias = "BLUETOOTH_ADVERTISE"
        ),
        @Permission(
            strings = {"android.permission.NEARBY_WIFI_DEVICES"},
            alias = "NEARBY_WIFI_DEVICES"
        ),
        @Permission(
            strings = {Manifest.permission.ACCESS_WIFI_STATE},
            alias = "ACCESS_WIFI_STATE"
        ),
        @Permission(
            strings = {Manifest.permission.CHANGE_WIFI_STATE},
            alias = "CHANGE_WIFI_STATE"
        )
    }
)
public class NearbyMultipeerPlugin extends Plugin {

    private NearbyMultipeer implementation;

    @Override
    public void load() {
        implementation = new NearbyMultipeer();
    }

    /**
     * Obtiene los permisos requeridos según la versión de Android
     * @return Array de permisos necesarios para la versión actual
     */
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 15+
            return new String[]{
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                "android.permission.NEARBY_WIFI_DEVICES"
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            return new String[]{
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
            return new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else { // Android 9 y anteriores
            return new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
    }

    /**
     * Verifica si se tienen todos los permisos necesarios según la versión de Android
     */
    private void requestRequiredPermissions(PluginCall call) {
        String[] requiredPermissions = getRequiredPermissions();

        // Convertir los permisos a sus alias para Capacitor
        String[] aliases = new String[requiredPermissions.length];
        for (int i = 0; i < requiredPermissions.length; i++) {
            String permission = requiredPermissions[i];

            // Obtener el alias correspondiente para cada permiso
            if (permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                aliases[i] = "ACCESS_COARSE_LOCATION";
            } else if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                aliases[i] = "ACCESS_FINE_LOCATION";
            } else if (permission.equals(Manifest.permission.BLUETOOTH)) {
                aliases[i] = "BLUETOOTH";
            } else if (permission.equals(Manifest.permission.BLUETOOTH_ADMIN)) {
                aliases[i] = "BLUETOOTH_ADMIN";
            } else if (permission.equals("android.permission.BLUETOOTH_SCAN")) {
                aliases[i] = "BLUETOOTH_SCAN";
            } else if (permission.equals("android.permission.BLUETOOTH_CONNECT")) {
                aliases[i] = "BLUETOOTH_CONNECT";
            } else if (permission.equals("android.permission.BLUETOOTH_ADVERTISE")) {
                aliases[i] = "BLUETOOTH_ADVERTISE";
            } else if (permission.equals("android.permission.NEARBY_WIFI_DEVICES")) {
                aliases[i] = "NEARBY_WIFI_DEVICES";
            } else if (permission.equals(Manifest.permission.ACCESS_WIFI_STATE)) {
                aliases[i] = "ACCESS_WIFI_STATE";
            } else if (permission.equals(Manifest.permission.CHANGE_WIFI_STATE)) {
                aliases[i] = "CHANGE_WIFI_STATE";
            }
        }

        // Solicitar permisos usando la API de Capacitor
        requestPermissionForAliases(aliases, call, "checkRequiredPermissions");
    }

    @PermissionCallback
    private void checkRequiredPermissions(PluginCall call) {
        Log.d("NearbyMultipeerPlugin", "checkRequiredPermissions callback");

        // Verificar cada permiso requerido
        String[] requiredPermissions = getRequiredPermissions();
        boolean allGranted = true;

        // Verificar cada permiso mediante getPermissionState
        for (String permission : requiredPermissions) {
            String alias = null;

            // Obtener el alias correspondiente para cada permiso
            if (permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                alias = "ACCESS_COARSE_LOCATION";
            } else if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                alias = "ACCESS_FINE_LOCATION";
            } else if (permission.equals(Manifest.permission.BLUETOOTH)) {
                alias = "BLUETOOTH";
            } else if (permission.equals(Manifest.permission.BLUETOOTH_ADMIN)) {
                alias = "BLUETOOTH_ADMIN";
            } else if (permission.equals("android.permission.BLUETOOTH_SCAN")) {
                alias = "BLUETOOTH_SCAN";
            } else if (permission.equals("android.permission.BLUETOOTH_CONNECT")) {
                alias = "BLUETOOTH_CONNECT";
            } else if (permission.equals("android.permission.BLUETOOTH_ADVERTISE")) {
                alias = "BLUETOOTH_ADVERTISE";
            } else if (permission.equals("android.permission.NEARBY_WIFI_DEVICES")) {
                alias = "NEARBY_WIFI_DEVICES";
            } else if (permission.equals(Manifest.permission.ACCESS_WIFI_STATE)) {
                alias = "ACCESS_WIFI_STATE";
            } else if (permission.equals(Manifest.permission.CHANGE_WIFI_STATE)) {
                alias = "CHANGE_WIFI_STATE";
            }

            if (alias != null && getPermissionState(alias) != PermissionState.GRANTED) {
                Log.w("NearbyMultipeerPlugin", "Permiso no concedido: " + alias);
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            Log.w("NearbyMultipeerPlugin", "No se han concedido todos los permisos necesarios");
            call.reject("Se requieren permisos para utilizar Nearby Connections");
            return;
        }

        // Si llegamos aquí, todos los permisos están concedidos
        if (call.getData().has("serviceId")) {
            // Si tenemos un serviceId, procedemos con la inicialización
            String serviceIdValue = call.getString("serviceId");
            Log.d("NearbyMultipeerPlugin", "Inicializando con serviceId: " + serviceIdValue);

            implementation.initialize(getContext(), serviceIdValue, connectionLifecycleCallback,
                                    endpointDiscoveryCallback, payloadCallback);
            call.resolve();
        } else {
            // Si no tenemos serviceId, simplemente resolvemos la llamada
            call.resolve();
        }
    }

    @PluginMethod
    public void initialize(PluginCall call) {
        String serviceIdValue = call.getString("serviceId");
        if (serviceIdValue == null) {
            call.reject("serviceId is required");
            return;
        }

        // Primero solicitamos los permisos necesarios
        // El método checkRequiredPermissions manejará la inicialización cuando los permisos estén concedidos
        requestRequiredPermissions(call);
    }

    @PluginMethod
    public void startAdvertising(PluginCall call) {
        String name = call.getString("displayName");
        if (name == null) {
            name = "AndroidDevice";
        }

        implementation.startAdvertising(name, new NearbyMultipeer.OnResultListener() {
            @Override
            public void onSuccess() {
                call.resolve();
            }

            @Override
            public void onFailure(String error) {
                call.reject("Advertising failed: " + error);
            }
        });
    }

    @PluginMethod
    public void stopAdvertising(PluginCall call) {
        implementation.stopAdvertising();
        call.resolve();
    }

    @PluginMethod
    public void startDiscovery(PluginCall call) {
        implementation.startDiscovery(new NearbyMultipeer.OnResultListener() {
            @Override
            public void onSuccess() {
                call.resolve();
            }

            @Override
            public void onFailure(String error) {
                call.reject("Discovery failed: " + error);
            }
        });
    }

    @PluginMethod
    public void stopDiscovery(PluginCall call) {
        implementation.stopDiscovery();
        call.resolve();
    }

    @PluginMethod
    public void connect(PluginCall call) {
        String endpointId = call.getString("endpointId");
        if (endpointId == null) {
            call.reject("endpointId required");
            return;
        }

        String name = call.getString("displayName");
        if (name == null) {
            name = "AndroidDevice";
        }

        implementation.requestConnection(name, endpointId, new NearbyMultipeer.OnResultListener() {
            @Override
            public void onSuccess() {
                call.resolve();
            }

            @Override
            public void onFailure(String error) {
                call.reject("Connect failed: " + error);
            }
        });
    }

    @PluginMethod
    public void acceptConnection(PluginCall call) {
        String endpointId = call.getString("endpointId");
        if (endpointId == null) {
            call.reject("endpointId required");
            return;
        }

        implementation.acceptConnection(endpointId, new NearbyMultipeer.OnResultListener() {
            @Override
            public void onSuccess() {
                call.resolve();
            }

            @Override
            public void onFailure(String error) {
                call.reject("Accept connection failed: " + error);
            }
        });
    }

    @PluginMethod
    public void rejectConnection(PluginCall call) {
        String endpointId = call.getString("endpointId");
        if (endpointId == null) {
            call.reject("endpointId required");
            return;
        }

        implementation.rejectConnection(endpointId, new NearbyMultipeer.OnResultListener() {
            @Override
            public void onSuccess() {
                call.resolve();
            }

            @Override
            public void onFailure(String error) {
                call.reject("Reject connection failed: " + error);
            }
        });
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        implementation.disconnectFromAllEndpoints();
        call.resolve();
    }

    @PluginMethod
    public void disconnectFromEndpoint(PluginCall call) {
        String endpointId = call.getString("endpointId");
        if (endpointId == null) {
            call.reject("endpointId required");
            return;
        }

        implementation.disconnectFromEndpoint(endpointId);
        call.resolve();
    }

    @PluginMethod
    public void sendMessage(PluginCall call) {
        String endpointId = call.getString("endpointId");
        if (endpointId == null) {
            call.reject("endpointId required");
            return;
        }

        String dataStr = call.getString("data");
        if (dataStr == null) {
            call.reject("data required");
            return;
        }

        implementation.sendMessage(endpointId, dataStr, new NearbyMultipeer.OnResultListener() {
            @Override
            public void onSuccess() {
                call.resolve();
            }

            @Override
            public void onFailure(String error) {
                call.reject("Send message failed: " + error);
            }
        });
    }

    @PluginMethod
    public void setStrategy(PluginCall call) {
        String strategyName = call.getString("strategy");
        if (strategyName == null) {
            call.reject("strategy required");
            return;
        }

        Strategy strategy;
        switch (strategyName.toUpperCase()) {
            case "P2P_CLUSTER":
                strategy = Strategy.P2P_CLUSTER;
                break;
            case "P2P_STAR":
                strategy = Strategy.P2P_STAR;
                break;
            case "P2P_POINT_TO_POINT":
                strategy = Strategy.P2P_POINT_TO_POINT;
                break;
            default:
                call.reject("Invalid strategy: " + strategyName);
                return;
        }

        implementation.setStrategy(strategy);
        call.resolve();
    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            JSObject jsObject = new JSObject();
            jsObject.put("endpointId", endpointId);
            jsObject.put("endpointName", info.getEndpointName());
            jsObject.put("authenticationToken", info.getAuthenticationDigits());
            jsObject.put("isIncomingConnection", info.isIncomingConnection());
            notifyListeners("connectionRequested", jsObject);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            JSObject jsObject = new JSObject();
            jsObject.put("endpointId", endpointId);
            jsObject.put("status", result.getStatus().getStatusCode());
            notifyListeners("connectionResult", jsObject);
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            JSObject jsObject = new JSObject();
            jsObject.put("endpointId", endpointId);
            notifyListeners("endpointLost", jsObject);
        }
    };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            JSObject jsObject = new JSObject();
            jsObject.put("endpointId", endpointId);
            jsObject.put("endpointName", info.getEndpointName());
            jsObject.put("serviceId", info.getServiceId());
            notifyListeners("endpointFound", jsObject);
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            JSObject jsObject = new JSObject();
            jsObject.put("endpointId", endpointId);
            notifyListeners("endpointLost", jsObject);
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            String msg = new String(payload.asBytes());
            JSObject jsObject = new JSObject();
            jsObject.put("endpointId", endpointId);
            jsObject.put("data", msg);
            notifyListeners("message", jsObject);
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            JSObject jsObject = new JSObject();
            jsObject.put("endpointId", endpointId);
            jsObject.put("bytesTransferred", update.getBytesTransferred());
            jsObject.put("totalBytes", update.getTotalBytes());
            jsObject.put("status", update.getStatus());
            notifyListeners("payloadTransferUpdate", jsObject);
        }
    };
}
