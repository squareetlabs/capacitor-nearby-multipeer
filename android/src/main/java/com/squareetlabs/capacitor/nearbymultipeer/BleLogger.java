package com.squareetlabs.capacitor.nearbymultipeer;

import android.util.Log;

/**
 * Sistema de logging mejorado para operaciones BLE
 */
public class BleLogger {
    private static final String TAG = "NearbyMultipeer";
    private static LogLevel logLevel = LogLevel.INFO;
    
    /**
     * Niveles de log
     */
    public enum LogLevel {
        NONE(0),   // No logging
        ERROR(1),  // Solo errores
        WARN(2),   // Errores y advertencias
        INFO(3),   // Errores, advertencias e info general
        DEBUG(4),  // Información detallada
        VERBOSE(5); // Información muy detallada
        
        private final int level;
        
        LogLevel(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * Establece el nivel de log
     * @param level Nivel de log
     */
    public static void setLogLevel(LogLevel level) {
        logLevel = level;
    }
    
    /**
     * Log de error
     * @param message Mensaje
     */
    public static void error(String message) {
        if (logLevel.getLevel() >= LogLevel.ERROR.getLevel()) {
            Log.e(TAG, message);
        }
    }
    
    /**
     * Log de error con excepción
     * @param message Mensaje
     * @param throwable Excepción
     */
    public static void error(String message, Throwable throwable) {
        if (logLevel.getLevel() >= LogLevel.ERROR.getLevel()) {
            Log.e(TAG, message, throwable);
        }
    }
    
    /**
     * Log de advertencia
     * @param message Mensaje
     */
    public static void warn(String message) {
        if (logLevel.getLevel() >= LogLevel.WARN.getLevel()) {
            Log.w(TAG, message);
        }
    }
    
    /**
     * Log de info
     * @param message Mensaje
     */
    public static void info(String message) {
        if (logLevel.getLevel() >= LogLevel.INFO.getLevel()) {
            Log.i(TAG, message);
        }
    }
    
    /**
     * Log de debug
     * @param message Mensaje
     */
    public static void debug(String message) {
        if (logLevel.getLevel() >= LogLevel.DEBUG.getLevel()) {
            Log.d(TAG, message);
        }
    }
    
    /**
     * Log verbose
     * @param message Mensaje
     */
    public static void verbose(String message) {
        if (logLevel.getLevel() >= LogLevel.VERBOSE.getLevel()) {
            Log.v(TAG, message);
        }
    }
    
    /**
     * Log específico para operaciones BLE
     * @param operation Operación
     * @param deviceId ID del dispositivo
     * @param details Detalles
     */
    public static void logBleOperation(String operation, String deviceId, String details) {
        debug(String.format("BLE %s - Device: %s - %s", operation, deviceId != null ? deviceId : "N/A", details));
    }
    
    /**
     * Log para datos hexadecimales (útil para depurar payloads)
     * @param label Etiqueta
     * @param data Datos
     */
    public static void logHexData(String label, byte[] data) {
        if (logLevel.getLevel() >= LogLevel.DEBUG.getLevel() && data != null) {
            StringBuilder hex = new StringBuilder();
            for (byte b : data) {
                hex.append(String.format("%02X ", b));
            }
            debug(label + ": " + hex.toString());
        }
    }
} 