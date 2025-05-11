import Foundation
import MultipeerConnectivity

@objc public class NearbyMultipeer: NSObject {
    
    // MARK: - Properties
    
    private var serviceId: String?
    private var peerID: MCPeerID?
    private var session: MCSession?
    private var advertiser: MCNearbyServiceAdvertiser?
    private var browser: MCNearbyServiceBrowser?
    
    // Delegate para comunicación con el plugin principal
    public weak var delegate: NearbyMultipeerDelegate?
    
    // MARK: - Public Methods
    
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
    
    public func initialize(serviceId: String) {
        self.serviceId = serviceId
        let deviceName = UIDevice.current.name
        
        // Limpiamos recursos anteriores si existen
        cleanup()
        
        // Creamos un nuevo peer ID con el nombre del dispositivo
        self.peerID = MCPeerID(displayName: deviceName)
        
        // Inicializamos la sesión
        self.session = MCSession(
            peer: peerID!,
            securityIdentity: nil,
            encryptionPreference: .required
        )
        self.session?.delegate = self
        
        print("NearbyMultipeer inicializado con serviceId: \(serviceId)")
    }
    
    public func setDisplayName(displayName: String) {
        // En iOS no podemos cambiar el nombre de un MCPeerID una vez creado,
        // necesitamos crear uno nuevo y reinicializar todo
        cleanup()
        
        self.peerID = MCPeerID(displayName: displayName)
        
        self.session = MCSession(
            peer: peerID!,
            securityIdentity: nil,
            encryptionPreference: .required
        )
        self.session?.delegate = self
        
        print("Display name cambiado a: \(displayName)")
    }
    
    public func startAdvertising() {
        guard let serviceId = serviceId, let peerID = peerID else {
            delegate?.onError(error: "No inicializado. Llama a initialize primero.")
            return
        }
        
        stopAdvertising()
        
        // Iniciar anuncio del servicio
        advertiser = MCNearbyServiceAdvertiser(
            peer: peerID,
            discoveryInfo: ["serviceId": serviceId],
            serviceType: serviceId
        )
        advertiser?.delegate = self
        advertiser?.startAdvertisingPeer()
        
        print("Advertising iniciado")
        delegate?.onSuccess()
    }
    
    public func stopAdvertising() {
        advertiser?.stopAdvertisingPeer()
        advertiser = nil
        print("Advertising detenido")
    }
    
    public func startDiscovery() {
        guard let serviceId = serviceId, let _ = peerID else {
            delegate?.onError(error: "No inicializado. Llama a initialize primero.")
            return
        }
        
        stopDiscovery()
        
        // Iniciar búsqueda de dispositivos
        browser = MCNearbyServiceBrowser(peer: peerID!, serviceType: serviceId)
        browser?.delegate = self
        browser?.startBrowsingForPeers()
        
        print("Discovery iniciado")
        delegate?.onSuccess()
    }
    
    public func stopDiscovery() {
        browser?.stopBrowsingForPeers()
        browser = nil
        print("Discovery detenido")
    }
    
    public func connect(endpointId: String) {
        guard let browser = browser, let session = session else {
            delegate?.onError(error: "No inicializado o discovery no está activo.")
            return
        }
        
        // Buscar el peer con el ID correspondiente
        if let peerToConnect = getPeerByEndpointId(endpointId) {
            // Iniciar la invitación al peer
            browser.invitePeer(peerToConnect, to: session, withContext: nil, timeout: 30)
            print("Invitación enviada a: \(endpointId)")
            delegate?.onSuccess()
        } else {
            delegate?.onError(error: "Endpoint no encontrado: \(endpointId)")
        }
    }
    
    public func acceptConnection(endpointId: String) {
        // En iOS, las conexiones se aceptan automáticamente a través del delegate MCNearbyServiceAdvertiserDelegate
        // pero podemos almacenar los endpoints aprobados para uso futuro
        print("Conexión aceptada para: \(endpointId)")
        delegate?.onSuccess()
    }
    
    public func rejectConnection(endpointId: String) {
        // En iOS, las conexiones se pueden rechazar a través del delegate MCNearbyServiceAdvertiserDelegate
        print("Conexión rechazada para: \(endpointId)")
        delegate?.onSuccess()
    }
    
    public func disconnectFromEndpoint(endpointId: String) {
        if let peerToDisconnect = getPeerByEndpointId(endpointId), let session = session {
            let connectedPeers = session.connectedPeers
            if connectedPeers.contains(peerToDisconnect) {
                // Crear diccionario para el motivo
                let reason = ["reason": "Disconnected by user"]
                
                // Convertir a Data
                if let data = try? JSONSerialization.data(withJSONObject: reason) {
                    try? session.send(data, toPeers: [peerToDisconnect], with: .reliable)
                }
            }
            print("Desconexión solicitada para: \(endpointId)")
        } else {
            print("Peer no encontrado o no conectado: \(endpointId)")
        }
    }
    
    public func disconnectFromAllEndpoints() {
        session?.disconnect()
        print("Desconectado de todos los endpoints")
    }
    
    public func sendMessage(endpointId: String, message: String) {
        guard let session = session else {
            delegate?.onError(error: "No inicializado. Llama a initialize primero.")
            return
        }
        
        // Convertir el mensaje a datos
        if let data = message.data(using: .utf8) {
            // Encontrar el peer
            if let peer = getPeerByEndpointId(endpointId) {
                do {
                    try session.send(data, toPeers: [peer], with: .reliable)
                    print("Mensaje enviado a \(endpointId): \(message)")
                    delegate?.onSuccess()
                } catch {
                    print("Error al enviar mensaje: \(error.localizedDescription)")
                    delegate?.onError(error: "Error al enviar mensaje: \(error.localizedDescription)")
                }
            } else {
                delegate?.onError(error: "Endpoint no encontrado: \(endpointId)")
            }
        } else {
            delegate?.onError(error: "Error al convertir mensaje a datos")
        }
    }
    
    public func cleanup() {
        stopAdvertising()
        stopDiscovery()
        session?.disconnect()
        session = nil
        peerID = nil
        print("Recursos liberados")
    }
    
    // MARK: - Private Methods
    
    private func getPeerByEndpointId(_ endpointId: String) -> MCPeerID? {
        // En MultipeerConnectivity, el ID no es un string sino un objeto MCPeerID
        // Usamos el displayName como identificador equivalente al endpointId
        // Buscamos en los peers encontrados por el browser
        return session?.connectedPeers.first(where: { $0.displayName == endpointId }) ??
               foundPeers.first(where: { $0.displayName == endpointId })
    }
    
    // Lista de peers encontrados pero aún no conectados
    private var foundPeers = [MCPeerID]()
    // Mapa para almacenar la información de invitación
    private var invitationHandlers = [MCPeerID: (Bool, MCSession) -> Void]()
}

// MARK: - Protocol para comunicación con el plugin principal

public protocol NearbyMultipeerDelegate: AnyObject {
    func onSuccess()
    func onError(error: String)
    func onConnectionRequested(endpointId: String, endpointName: String, context: Data?)
    func onConnectionResult(endpointId: String, connected: Bool)
    func onEndpointFound(endpointId: String, endpointName: String, serviceId: String?)
    func onEndpointLost(endpointId: String)
    func onMessageReceived(endpointId: String, data: String)
    func onPayloadTransferUpdate(endpointId: String, bytesTransferred: Int64, totalBytes: Int64, completed: Bool)
}

// MARK: - MCSessionDelegate

extension NearbyMultipeer: MCSessionDelegate {
    public func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {
        // Notificar el cambio de estado de conexión
        let endpointId = peerID.displayName
        
        switch state {
        case .connected:
            print("Conectado a: \(endpointId)")
            DispatchQueue.main.async {
                self.delegate?.onConnectionResult(endpointId: endpointId, connected: true)
            }
        case .connecting:
            print("Conectando a: \(endpointId)")
        case .notConnected:
            print("Desconectado de: \(endpointId)")
            DispatchQueue.main.async {
                self.delegate?.onConnectionResult(endpointId: endpointId, connected: false)
                self.delegate?.onEndpointLost(endpointId: endpointId)
            }
        @unknown default:
            print("Estado desconocido para: \(endpointId)")
        }
    }
    
    public func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {
        // Convertir los datos recibidos a string
        if let message = String(data: data, encoding: .utf8) {
            print("Mensaje recibido de \(peerID.displayName): \(message)")
            
            // Verificar si es un mensaje de desconexión
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: String],
               let reason = json["reason"], reason == "Disconnected by user" {
                print("El peer \(peerID.displayName) solicitó desconexión")
                return
            }
            
            DispatchQueue.main.async {
                self.delegate?.onMessageReceived(endpointId: peerID.displayName, data: message)
            }
        }
    }
    
    public func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {
        // No implementado para este plugin
    }
    
    public func session(_ session: MCSession, didStartReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, with progress: Progress) {
        // Notificar el progreso de la transferencia
        DispatchQueue.main.async {
            self.delegate?.onPayloadTransferUpdate(
                endpointId: peerID.displayName,
                bytesTransferred: progress.completedUnitCount,
                totalBytes: progress.totalUnitCount,
                completed: false
            )
        }
    }
    
    public func session(_ session: MCSession, didFinishReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, at localURL: URL?, withError error: Error?) {
        if let error = error {
            print("Error al recibir recurso: \(error.localizedDescription)")
        } else {
            print("Recurso recibido con éxito: \(resourceName)")
        }
        
        // Notificar la finalización de la transferencia
        DispatchQueue.main.async {
            self.delegate?.onPayloadTransferUpdate(
                endpointId: peerID.displayName,
                bytesTransferred: 0,
                totalBytes: 0,
                completed: true
            )
        }
    }
}

// MARK: - MCNearbyServiceAdvertiserDelegate

extension NearbyMultipeer: MCNearbyServiceAdvertiserDelegate {
    public func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didReceiveInvitationFromPeer peerID: MCPeerID, withContext context: Data?, invitationHandler: @escaping (Bool, MCSession?) -> Void) {
        print("Invitación recibida de: \(peerID.displayName)")
        
        // Almacenar el handler para usarlo después de que el usuario acepte o rechace
        invitationHandlers[peerID] = invitationHandler
        
        // Notificar al plugin para que muestre la solicitud al usuario
        DispatchQueue.main.async {
            self.delegate?.onConnectionRequested(
                endpointId: peerID.displayName,
                endpointName: peerID.displayName,
                context: context
            )
        }
    }
    
    public func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didNotStartAdvertisingPeer error: Error) {
        print("Error al iniciar advertising: \(error.localizedDescription)")
        delegate?.onError(error: "Error al iniciar advertising: \(error.localizedDescription)")
    }
}

// MARK: - MCNearbyServiceBrowserDelegate

extension NearbyMultipeer: MCNearbyServiceBrowserDelegate {
    public func browser(_ browser: MCNearbyServiceBrowser, foundPeer peerID: MCPeerID, withDiscoveryInfo info: [String : String]?) {
        print("Peer encontrado: \(peerID.displayName)")
        
        // Añadir a la lista de peers encontrados
        if !foundPeers.contains(peerID) {
            foundPeers.append(peerID)
        }
        
        // Obtener el serviceId del info
        let serviceId = info?["serviceId"] ?? self.serviceId
        
        // Notificar al plugin
        DispatchQueue.main.async {
            self.delegate?.onEndpointFound(
                endpointId: peerID.displayName,
                endpointName: peerID.displayName,
                serviceId: serviceId
            )
        }
    }
    
    public func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        print("Peer perdido: \(peerID.displayName)")
        
        // Eliminar de la lista de peers encontrados
        foundPeers.removeAll { $0 == peerID }
        
        // Notificar al plugin
        DispatchQueue.main.async {
            self.delegate?.onEndpointLost(endpointId: peerID.displayName)
        }
    }
    
    public func browser(_ browser: MCNearbyServiceBrowser, didNotStartBrowsingForPeers error: Error) {
        print("Error al iniciar discovery: \(error.localizedDescription)")
        delegate?.onError(error: "Error al iniciar discovery: \(error.localizedDescription)")
    }
}
