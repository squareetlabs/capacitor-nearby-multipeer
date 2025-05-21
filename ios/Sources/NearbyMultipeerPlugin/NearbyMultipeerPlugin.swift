import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(NearbyMultipeerPlugin)
public class NearbyMultipeerPlugin: CAPPlugin, CAPBridgedPlugin, NearbyMultipeerDelegate {
    public let identifier = "NearbyMultipeerPlugin"
    public let jsName = "NearbyMultipeer"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "echo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "initialize", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setStrategy", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startAdvertising", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopAdvertising", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startDiscovery", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopDiscovery", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "connect", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "acceptConnection", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "rejectConnection", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "disconnectFromEndpoint", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "disconnect", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "sendMessage", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setLogLevel", returnType: CAPPluginReturnPromise)
    ]
    
    private let implementation = NearbyMultipeer()
    private var lastCall: CAPPluginCall?

    public override func load() {
        implementation.delegate = self
    }
    
    @objc func echo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve([
            "value": implementation.echo(value)
        ])
    }
    
    @objc func initialize(_ call: CAPPluginCall) {
        guard let serviceId = call.getString("serviceId") else {
            call.reject("serviceId es requerido")
            return
        }
        
        implementation.initialize(serviceId: serviceId)
        call.resolve()
    }
    
    @objc func setStrategy(_ call: CAPPluginCall) {
        // iOS no utiliza estrategias como Android, pero mantenemos el método
        // para compatibilidad con la API
        call.resolve()
    }
    
    @objc func startAdvertising(_ call: CAPPluginCall) {
        if let displayName = call.getString("displayName") {
            implementation.setDisplayName(displayName: displayName)
        }
        
        lastCall = call
        implementation.startAdvertising()
    }
    
    @objc func stopAdvertising(_ call: CAPPluginCall) {
        implementation.stopAdvertising()
        call.resolve()
    }
    
    @objc func startDiscovery(_ call: CAPPluginCall) {
        lastCall = call
        implementation.startDiscovery()
    }
    
    @objc func stopDiscovery(_ call: CAPPluginCall) {
        implementation.stopDiscovery()
        call.resolve()
    }
    
    @objc func connect(_ call: CAPPluginCall) {
        guard let endpointId = call.getString("endpointId") else {
            call.reject("endpointId es requerido")
            return
        }
        
        if let displayName = call.getString("displayName") {
            implementation.setDisplayName(displayName: displayName)
        }
        
        lastCall = call
        implementation.connect(endpointId: endpointId)
    }
    
    @objc func acceptConnection(_ call: CAPPluginCall) {
        guard let endpointId = call.getString("endpointId") else {
            call.reject("endpointId es requerido")
            return
        }
        
        lastCall = call
        implementation.acceptConnection(endpointId: endpointId)
    }
    
    @objc func rejectConnection(_ call: CAPPluginCall) {
        guard let endpointId = call.getString("endpointId") else {
            call.reject("endpointId es requerido")
            return
        }
        
        lastCall = call
        implementation.rejectConnection(endpointId: endpointId)
    }
    
    @objc func disconnectFromEndpoint(_ call: CAPPluginCall) {
        guard let endpointId = call.getString("endpointId") else {
            call.reject("endpointId es requerido")
            return
        }
        
        implementation.disconnectFromEndpoint(endpointId: endpointId)
        call.resolve()
    }
    
    @objc func disconnect(_ call: CAPPluginCall) {
        implementation.disconnectFromAllEndpoints()
        call.resolve()
    }
    
    @objc func sendMessage(_ call: CAPPluginCall) {
        guard let endpointId = call.getString("endpointId") else {
            call.reject("endpointId es requerido")
            return
        }
        
        guard let data = call.getString("data") else {
            call.reject("data es requerido")
            return
        }
        
        lastCall = call
        implementation.sendMessage(endpointId: endpointId, message: data)
    }
    
    @objc func setLogLevel(_ call: CAPPluginCall) {
        let logLevel = call.getInt("logLevel", 3) // default: info
        
        guard let level = BleLogger.LogLevel(rawValue: logLevel) else {
            call.reject("Nivel de log inválido")
            return
        }
        
        BleLogger.logLevel = level
        BleLogger.info("Nivel de log establecido a: \(level)")
        
        call.resolve()
    }
    
    // MARK: - NearbyMultipeerDelegate
    
    public func onSuccess() {
        if let call = lastCall {
            call.resolve()
            lastCall = nil
        }
    }
    
    public func onError(error: String) {
        if let call = lastCall {
            call.reject(error)
            lastCall = nil
        }
    }
    
    public func onConnectionRequested(endpointId: String, endpointName: String, context: Data?) {
        // Convertir authenticationToken de Data a String si existe
        let authToken = context?.base64EncodedString() ?? ""
        
        let data: [String: Any] = [
            "endpointId": endpointId,
            "endpointName": endpointName,
            "authenticationToken": authToken,
            "isIncomingConnection": true
        ]
        
        notifyListeners("connectionRequested", data: data)
    }
    
    public func onConnectionResult(endpointId: String, connected: Bool) {
        let status = connected ? 0 : -1 // 0 = success, -1 = error
        
        let data: [String: Any] = [
            "endpointId": endpointId,
            "status": status
        ]
        
        notifyListeners("connectionResult", data: data)
    }
    
    public func onEndpointFound(endpointId: String, endpointName: String, serviceId: String?) {
        let data: [String: Any] = [
            "endpointId": endpointId,
            "endpointName": endpointName,
            "serviceId": serviceId ?? ""
        ]
        
        notifyListeners("endpointFound", data: data)
    }
    
    public func onEndpointLost(endpointId: String) {
        let data: [String: Any] = [
            "endpointId": endpointId
        ]
        
        notifyListeners("endpointLost", data: data)
    }
    
    public func onMessageReceived(endpointId: String, data messageData: String) {
        let data: [String: Any] = [
            "endpointId": endpointId,
            "data": messageData
        ]
        
        notifyListeners("message", data: data)
    }
    
    public func onPayloadTransferUpdate(endpointId: String, bytesTransferred: Int64, totalBytes: Int64, completed: Bool) {
        let status = completed ? 3 : 2 // 2 = in_progress, 3 = success
        
        let data: [String: Any] = [
            "endpointId": endpointId,
            "bytesTransferred": bytesTransferred,
            "totalBytes": totalBytes,
            "status": status
        ]
        
        notifyListeners("payloadTransferUpdate", data: data)
    }
}
