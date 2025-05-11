package com.squareetlabs.capacitor.nearbymultipeer;

import android.content.Context;
import android.util.Log;


import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.Strategy;

public class NearbyMultipeer {
    private static final String TAG = "NearbyMultipeer";
    
    private ConnectionsClient connectionsClient;
    private String serviceId;
    private Strategy strategy = Strategy.P2P_STAR;
    
    // Callbacks para las interacciones de Nearby
    private ConnectionLifecycleCallback connectionLifecycleCallback;
    private EndpointDiscoveryCallback endpointDiscoveryCallback;
    private PayloadCallback payloadCallback;

    public NearbyMultipeer() {
        // Constructor por defecto
    }
    
    public void initialize(Context context, String serviceId, 
                          ConnectionLifecycleCallback connectionCallback, 
                          EndpointDiscoveryCallback discoveryCallback, 
                          PayloadCallback payloadCallback) {
        this.serviceId = serviceId;
        this.connectionLifecycleCallback = connectionCallback;
        this.endpointDiscoveryCallback = discoveryCallback;
        this.payloadCallback = payloadCallback;
        this.connectionsClient = Nearby.getConnectionsClient(context);
        
        Log.i(TAG, "NearbyMultipeer inicializado con serviceId: " + serviceId);
    }
    
    public void setStrategy(Strategy newStrategy) {
        this.strategy = newStrategy;
        Log.i(TAG, "Estrategia cambiada a: " + strategy.toString());
    }
    
    public String echo(String value) {
        Log.i("Echo", value);
        return value;
    }
    
    public void startAdvertising(String displayName, OnResultListener listener) {
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }
        
        Log.i(TAG, "Iniciando advertising como: " + displayName);
        connectionsClient.startAdvertising(
                displayName,
                serviceId,
                connectionLifecycleCallback,
                new AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener(unused -> {
            Log.i(TAG, "Advertising iniciado con éxito");
            listener.onSuccess();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al iniciar advertising", e);
            listener.onFailure("Error al iniciar advertising: " + e.getMessage());
        });
    }
    
    public void stopAdvertising() {
        if (connectionsClient != null) {
            connectionsClient.stopAdvertising();
            Log.i(TAG, "Advertising detenido");
        }
    }
    
    public void startDiscovery(OnResultListener listener) {
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }
        
        Log.i(TAG, "Iniciando discovery para serviceId: " + serviceId);
        connectionsClient.startDiscovery(
                serviceId,
                endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener(unused -> {
            Log.i(TAG, "Discovery iniciado con éxito");
            listener.onSuccess();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al iniciar discovery", e);
            listener.onFailure("Error al iniciar discovery: " + e.getMessage());
        });
    }
    
    public void stopDiscovery() {
        if (connectionsClient != null) {
            connectionsClient.stopDiscovery();
            Log.i(TAG, "Discovery detenido");
        }
    }
    
    public void requestConnection(String displayName, String endpointId, OnResultListener listener) {
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }
        
        Log.i(TAG, "Solicitando conexión a endpoint: " + endpointId);
        connectionsClient.requestConnection(
                displayName,
                endpointId,
                connectionLifecycleCallback
        ).addOnSuccessListener(unused -> {
            Log.i(TAG, "Solicitud de conexión enviada con éxito");
            listener.onSuccess();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error al solicitar conexión", e);
            listener.onFailure("Error al solicitar conexión: " + e.getMessage());
        });
    }
    
    public void acceptConnection(String endpointId, OnResultListener listener) {
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }
        
        Log.i(TAG, "Aceptando conexión de endpoint: " + endpointId);
        connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener(unused -> {
                    Log.i(TAG, "Conexión aceptada con éxito");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al aceptar conexión", e);
                    listener.onFailure("Error al aceptar conexión: " + e.getMessage());
                });
    }
    
    public void rejectConnection(String endpointId, OnResultListener listener) {
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }
        
        Log.i(TAG, "Rechazando conexión de endpoint: " + endpointId);
        connectionsClient.rejectConnection(endpointId)
                .addOnSuccessListener(unused -> {
                    Log.i(TAG, "Conexión rechazada con éxito");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al rechazar conexión", e);
                    listener.onFailure("Error al rechazar conexión: " + e.getMessage());
                });
    }
    
    public void sendMessage(String endpointId, String message, OnResultListener listener) {
        if (connectionsClient == null) {
            listener.onFailure("No inicializado. Llama a initialize primero.");
            return;
        }
        
        byte[] bytes = message.getBytes();
        Payload payload = Payload.fromBytes(bytes);
        
        Log.i(TAG, "Enviando mensaje a endpoint: " + endpointId);
        connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener(unused -> {
                    Log.i(TAG, "Mensaje enviado con éxito");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al enviar mensaje", e);
                    listener.onFailure("Error al enviar mensaje: " + e.getMessage());
                });
    }
    
    public void disconnectFromEndpoint(String endpointId) {
        if (connectionsClient != null) {
            connectionsClient.disconnectFromEndpoint(endpointId);
            Log.i(TAG, "Desconectado del endpoint: " + endpointId);
        }
    }
    
    public void disconnectFromAllEndpoints() {
        if (connectionsClient != null) {
            connectionsClient.stopAllEndpoints();
            Log.i(TAG, "Desconectado de todos los endpoints");
        }
    }
    
    public void cleanup() {
        if (connectionsClient != null) {
            stopAdvertising();
            stopDiscovery();
            disconnectFromAllEndpoints();
            Log.i(TAG, "Recursos liberados");
        }
    }
    
    // Interfaz para manejar resultados asíncronos
    public interface OnResultListener {
        void onSuccess();
        void onFailure(String error);
    }
}
