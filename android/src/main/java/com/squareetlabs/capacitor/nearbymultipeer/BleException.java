package com.squareetlabs.capacitor.nearbymultipeer;

/**
 * Clase base para excepciones BLE
 */
public class BleException extends Exception {
    public BleException(String message) {
        super(message);
    }

    public BleException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Excepción lanzada cuando hay un problema de conexión BLE
 */
class BleConnectionException extends BleException {
    public BleConnectionException(String message) {
        super(message);
    }

    public BleConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Excepción lanzada cuando hay un problema durante el escaneo BLE
 */
class BleScanException extends BleException {
    public BleScanException(String message) {
        super(message);
    }

    public BleScanException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Excepción lanzada cuando hay un problema de escritura/lectura de características BLE
 */
class BleCharacteristicException extends BleException {
    public BleCharacteristicException(String message) {
        super(message);
    }

    public BleCharacteristicException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Excepción lanzada cuando hay un problema con el servicio BLE
 */
class BleServiceException extends BleException {
    public BleServiceException(String message) {
        super(message);
    }

    public BleServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Excepción lanzada cuando hay un problema de timeout
 */
class BleTimeoutException extends BleException {
    public BleTimeoutException(String message) {
        super(message);
    }

    public BleTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
} 