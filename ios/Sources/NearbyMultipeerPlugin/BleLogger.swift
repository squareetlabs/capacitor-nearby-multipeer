import Foundation

/**
 * Sistema de logging mejorado para operaciones BLE
 */
class BleLogger {
    /// Niveles de log
    enum LogLevel: Int {
        case none = 0    // No logging
        case error = 1   // Solo errores
        case warn = 2    // Errores y advertencias
        case info = 3    // Errores, advertencias e info general
        case debug = 4   // Información detallada
        case verbose = 5 // Información muy detallada
    }
    
    /// Tag para los logs
    private static let tag = "NearbyMultipeer"
    
    /// Nivel de log actual
    static var logLevel: LogLevel = .info
    
    /// Habilitar/deshabilitar colores en los logs de consola
    static var enableColors = true
    
    /**
     * Log de error
     * - Parameter message: Mensaje
     */
    static func error(_ message: String) {
        if logLevel.rawValue >= LogLevel.error.rawValue {
            let formattedMessage = enableColors ? "\u{001B}[31m[\(tag)] ❌ \(message)\u{001B}[0m" : "[\(tag)] ❌ \(message)"
            NSLog(formattedMessage)
        }
    }
    
    /**
     * Log de error con errores nativos
     * - Parameters:
     *   - message: Mensaje
     *   - error: Error
     */
    static func error(_ message: String, error: Error) {
        if logLevel.rawValue >= LogLevel.error.rawValue {
            let errorDescription: String
            
            if let bleError = error as? BleError {
                errorDescription = bleError.localizedDescription
            } else {
                errorDescription = error.localizedDescription
            }
            
            let formattedMessage = enableColors ? "\u{001B}[31m[\(tag)] ❌ \(message): \(errorDescription)\u{001B}[0m" : "[\(tag)] ❌ \(message): \(errorDescription)"
            NSLog(formattedMessage)
        }
    }
    
    /**
     * Log de advertencia
     * - Parameter message: Mensaje
     */
    static func warn(_ message: String) {
        if logLevel.rawValue >= LogLevel.warn.rawValue {
            let formattedMessage = enableColors ? "\u{001B}[33m[\(tag)] ⚠️ \(message)\u{001B}[0m" : "[\(tag)] ⚠️ \(message)"
            NSLog(formattedMessage)
        }
    }
    
    /**
     * Log de info
     * - Parameter message: Mensaje
     */
    static func info(_ message: String) {
        if logLevel.rawValue >= LogLevel.info.rawValue {
            let formattedMessage = enableColors ? "\u{001B}[36m[\(tag)] ℹ️ \(message)\u{001B}[0m" : "[\(tag)] ℹ️ \(message)"
            NSLog(formattedMessage)
        }
    }
    
    /**
     * Log de debug
     * - Parameter message: Mensaje
     */
    static func debug(_ message: String) {
        if logLevel.rawValue >= LogLevel.debug.rawValue {
            let formattedMessage = enableColors ? "\u{001B}[32m[\(tag)] 🔍 \(message)\u{001B}[0m" : "[\(tag)] 🔍 \(message)"
            NSLog(formattedMessage)
        }
    }
    
    /**
     * Log verbose
     * - Parameter message: Mensaje
     */
    static func verbose(_ message: String) {
        if logLevel.rawValue >= LogLevel.verbose.rawValue {
            let formattedMessage = enableColors ? "\u{001B}[37m[\(tag)] 📝 \(message)\u{001B}[0m" : "[\(tag)] 📝 \(message)"
            NSLog(formattedMessage)
        }
    }
    
    /**
     * Log específico para operaciones BLE
     * - Parameters:
     *   - operation: Operación
     *   - deviceId: ID del dispositivo
     *   - details: Detalles
     */
    static func logBleOperation(_ operation: String, deviceId: String?, details: String) {
        debug("BLE \(operation) - Device: \(deviceId ?? "N/A") - \(details)")
    }
    
    /**
     * Log para datos hexadecimales (útil para depurar payloads)
     * - Parameters:
     *   - label: Etiqueta
     *   - data: Datos
     */
    static func logHexData(_ label: String, data: Data?) {
        if logLevel.rawValue >= LogLevel.debug.rawValue, let data = data {
            let hexString = data.map { String(format: "%02X ", $0) }.joined()
            debug("\(label): \(hexString)")
        }
    }
} 