import Foundation
import CoreBluetooth

/**
 * Errores específicos para operaciones BLE
 */
enum BleError: Error {
    // Errores generales
    case notInitialized
    case notSupported
    case permissionDenied
    
    // Errores de conexión
    case connectionFailed(String)
    case connectionTimeout(String)
    case deviceNotFound(String)
    case deviceDisconnected(String)
    
    // Errores de servicio/característica
    case serviceNotFound(String)
    case characteristicNotFound(String)
    case serviceDiscoveryFailed(String)
    
    // Errores de operaciones
    case readFailed(String)
    case writeFailed(String)
    case notifySetupFailed(String)
    
    // Errores de escaneo
    case scanStartFailed(String)
    case scanInProgress
    
    // Errores de advertising
    case advertisingStartFailed(String)
    case advertisingInProgress
    
    // Errores de formato
    case invalidUUID(String)
    case invalidData(String)
    
    // Obtener mensaje descriptivo
    var localizedDescription: String {
        switch self {
        case .notInitialized:
            return "BLE no inicializado. Llama a initialize primero."
        case .notSupported:
            return "BLE no soportado en este dispositivo."
        case .permissionDenied:
            return "Permiso de Bluetooth denegado."
            
        case .connectionFailed(let details):
            return "Error de conexión BLE: \(details)"
        case .connectionTimeout(let details):
            return "Timeout de conexión BLE: \(details)"
        case .deviceNotFound(let id):
            return "Dispositivo no encontrado: \(id)"
        case .deviceDisconnected(let id):
            return "Dispositivo desconectado: \(id)"
            
        case .serviceNotFound(let uuid):
            return "Servicio no encontrado: \(uuid)"
        case .characteristicNotFound(let uuid):
            return "Característica no encontrada: \(uuid)"
        case .serviceDiscoveryFailed(let details):
            return "Error al descubrir servicios: \(details)"
            
        case .readFailed(let details):
            return "Error al leer característica: \(details)"
        case .writeFailed(let details):
            return "Error al escribir característica: \(details)"
        case .notifySetupFailed(let details):
            return "Error al configurar notificaciones: \(details)"
            
        case .scanStartFailed(let details):
            return "Error al iniciar escaneo: \(details)"
        case .scanInProgress:
            return "Escaneo ya en progreso."
            
        case .advertisingStartFailed(let details):
            return "Error al iniciar advertising: \(details)"
        case .advertisingInProgress:
            return "Advertising ya en progreso."
            
        case .invalidUUID(let uuid):
            return "UUID inválido: \(uuid)"
        case .invalidData(let details):
            return "Datos inválidos: \(details)"
        }
    }
    
    // Convertir errores sistema a BleError
    static func fromSystemError(_ error: Error, operation: String) -> BleError {
        let nsError = error as NSError
        
        switch nsError.domain {
        case CBATTErrorDomain:
            return handleCBATTError(nsError, operation: operation)
        case CBErrorDomain:
            return handleCBError(nsError, operation: operation)
        default:
            return .connectionFailed("\(operation): \(nsError.localizedDescription)")
        }
    }
    
    private static func handleCBATTError(_ error: NSError, operation: String) -> BleError {
        switch error.code {
        case CBATTError.readNotPermitted.rawValue:
            return .readFailed("Lectura no permitida")
        case CBATTError.writeNotPermitted.rawValue:
            return .writeFailed("Escritura no permitida")
        case CBATTError.attributeNotFound.rawValue:
            return .characteristicNotFound("Atributo no encontrado")
        case CBATTError.requestNotSupported.rawValue:
            return .notSupported
        case CBATTError.invalidOffset.rawValue:
            return .readFailed("Offset inválido")
        case CBATTError.insufficientAuthentication.rawValue, CBATTError.insufficientEncryption.rawValue:
            return .connectionFailed("Autenticación/encriptación insuficiente")
        default:
            return .connectionFailed("\(operation): código de error \(error.code)")
        }
    }
    
    private static func handleCBError(_ error: NSError, operation: String) -> BleError {
        switch error.code {
        case CBError.unknown.rawValue:
            return .connectionFailed("Error desconocido")
        case CBError.invalidParameters.rawValue:
            return .invalidData("Parámetros inválidos")
        case CBError.invalidHandle.rawValue:
            return .connectionFailed("Handle inválido")
        case CBError.notConnected.rawValue:
            return .deviceDisconnected("Dispositivo no conectado")
        case CBError.outOfSpace.rawValue:
            return .writeFailed("Sin espacio disponible")
        case CBError.operationCancelled.rawValue:
            return .connectionFailed("Operación cancelada")
        case CBError.connectionTimeout.rawValue:
            return .connectionTimeout("Timeout de conexión")
        case CBError.peripheralDisconnected.rawValue:
            return .deviceDisconnected("Dispositivo desconectado")
        case CBError.uuidNotAllowed.rawValue:
            return .invalidUUID("UUID no permitido")
        case CBError.alreadyAdvertising.rawValue:
            return .advertisingInProgress
        default:
            return .connectionFailed("\(operation): código de error \(error.code)")
        }
    }
} 