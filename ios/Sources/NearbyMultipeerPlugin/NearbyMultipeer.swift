import Foundation
import MultipeerConnectivity
import CoreBluetooth

@objc public class NearbyMultipeer: NSObject {

    // MARK: - Properties

    private var serviceId: String?
    private var peerID: MCPeerID?
    private var session: MCSession?
    private var advertiser: MCNearbyServiceAdvertiser?
    private var browser: MCNearbyServiceBrowser?

    // Bluetooth related properties
    private var serviceUUID: CBUUID = CBUUID(string: "FA87C0D0-AFAC-11DE-8A39-0800200C9A66")
    private static let CHARACTERISTIC_UUID = CBUUID(string: "34B1CF4D-1069-4AD6-89B6-E161D79BE4D8")
    private var centralManager: CBCentralManager?
    private var peripheralManager: CBPeripheralManager?
    private var discoveredPeripherals = [String: CBPeripheral]()
    private var connectedPeripherals = [String: CBPeripheral]()
    private var transferCharacteristic: CBCharacteristic?
    private var serviceCharacteristic: CBMutableCharacteristic?
    private var isAdvertising = false
    private var isDiscovering = false
    private var isConnected = false
    private var deviceName: String = ""

    // Delegate para comunicación con el plugin principal
    public weak var delegate: NearbyMultipeerDelegate?

    // MARK: - Public Methods

    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }

    public func initialize(serviceId: String, serviceUUIDString: String? = nil) {
        self.serviceId = serviceId
        self.deviceName = "iOS_" + UIDevice.current.name // Prefix to identify iOS devices

        // Permitir configurar el UUID de servicio
        if let uuidString = serviceUUIDString, !uuidString.isEmpty {
            let formatted = NearbyMultipeer.formatBleUuid(uuidString)
            self.serviceUUID = CBUUID(string: formatted)
        } else {
            self.serviceUUID = CBUUID(string: "FA87C0D0-AFAC-11DE-8A39-0800200C9A66")
        }

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

        // Initialize Bluetooth managers
        centralManager = CBCentralManager(delegate: self, queue: nil)
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)

        print("NearbyMultipeer inicializado con serviceId: \(serviceId), serviceUUID: \(self.serviceUUID.uuidString)")
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

        // Iniciar anuncio del servicio para iOS devices
        advertiser = MCNearbyServiceAdvertiser(
            peer: peerID,
            discoveryInfo: ["serviceId": serviceId],
            serviceType: serviceId
        )
        advertiser?.delegate = self
        advertiser?.startAdvertisingPeer()

        // Start Bluetooth advertising for Android devices
        startBluetoothAdvertising()

        isAdvertising = true
        print("Advertising iniciado")
        delegate?.onSuccess()
    }

    private func startBluetoothAdvertising() {
        guard let peripheralManager = peripheralManager else {
            print("Bluetooth peripheral manager no disponible")
            return
        }

        // Check if Bluetooth is powered on
        if peripheralManager.state != .poweredOn {
            print("Bluetooth no está activado")
            return
        }

        // Create the service
        let service = CBMutableService(type: serviceUUID, primary: true)

        // Create the characteristic
        serviceCharacteristic = CBMutableCharacteristic(
            type: NearbyMultipeer.CHARACTERISTIC_UUID,
            properties: [.read, .write, .notify],
            value: nil,
            permissions: [.readable, .writeable]
        )

        // Add the characteristic to the service
        service.characteristics = [serviceCharacteristic!]

        // Add the service to the peripheral manager
        peripheralManager.add(service)

        // Start advertising
        peripheralManager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [serviceUUID],
            CBAdvertisementDataLocalNameKey: deviceName
        ])

        print("Bluetooth advertising iniciado")
    }

    public func stopAdvertising() {
        // Stop MultipeerConnectivity advertising
        advertiser?.stopAdvertisingPeer()
        advertiser = nil

        // Stop Bluetooth advertising
        peripheralManager?.stopAdvertising()

        isAdvertising = false
        print("Advertising detenido")
    }

    public func startDiscovery() {
        guard let serviceId = serviceId, let _ = peerID else {
            delegate?.onError(error: "No inicializado. Llama a initialize primero.")
            return
        }

        stopDiscovery()

        // Iniciar búsqueda de dispositivos iOS
        browser = MCNearbyServiceBrowser(peer: peerID!, serviceType: serviceId)
        browser?.delegate = self
        browser?.startBrowsingForPeers()

        // Start Bluetooth scanning for Android devices
        startBluetoothDiscovery()

        isDiscovering = true
        print("Discovery iniciado")
        delegate?.onSuccess()
    }

    private func startBluetoothDiscovery() {
        guard let centralManager = centralManager else {
            print("Bluetooth central manager no disponible")
            return
        }

        // Check if Bluetooth is powered on
        if centralManager.state != .poweredOn {
            print("Bluetooth no está activado")
            return
        }

        // Clear previously discovered peripherals
        discoveredPeripherals.removeAll()

        // Escanear usando el UUID configurado
        centralManager.scanForPeripherals(
            withServices: [serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
        print("Bluetooth discovery iniciado con UUID: \(serviceUUID.uuidString)")
    }

    public func stopDiscovery() {
        // Stop MultipeerConnectivity discovery
        browser?.stopBrowsingForPeers()
        browser = nil

        // Stop Bluetooth scanning
        centralManager?.stopScan()

        isDiscovering = false
        print("Discovery detenido")
    }

    public func connect(endpointId: String) {
        // Check if this is a Bluetooth peripheral (Android device)
        if let peripheral = discoveredPeripherals[endpointId] {
            // Connect to Android device via Bluetooth
            connectToBluetoothDevice(peripheral, endpointId: endpointId)
            delegate?.onSuccess()
        } else {
            // This is an iOS device, connect via MultipeerConnectivity
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
    }

    private func connectToBluetoothDevice(_ peripheral: CBPeripheral, endpointId: String) {
        guard let centralManager = centralManager else {
            print("Bluetooth central manager no disponible")
            return
        }

        // Stop scanning before connecting
        centralManager.stopScan()

        // Set the peripheral's delegate to self
        peripheral.delegate = self

        // Connect to the peripheral
        centralManager.connect(peripheral, options: nil)

        // Stop advertising once connected
        if isAdvertising {
            stopAdvertising()
        }

        print("Conectando a dispositivo Bluetooth: \(endpointId)")
    }

    public func acceptConnection(endpointId: String) {
        // Check if this is a Bluetooth connection (Android device)
        if connectedPeripherals[endpointId] != nil {
            // For Bluetooth, we've already accepted the connection when the peripheral connected
            // Just notify success
            print("Conexión Bluetooth aceptada para: \(endpointId)")

            // Stop advertising once connected
            if isAdvertising {
                stopAdvertising()
            }

            isConnected = true
            delegate?.onSuccess()

            // Notify connection result
            DispatchQueue.main.async {
                self.delegate?.onConnectionResult(endpointId: endpointId, connected: true)
            }
        } else {
            // For MultipeerConnectivity, we need to accept the invitation
            // Find the invitation handler for this peer
            if let peerID = getPeerByEndpointId(endpointId),
               let invitationHandler = invitationHandlers[peerID],
               let session = session {
                // Accept the invitation
                invitationHandler(true, session)
                invitationHandlers.removeValue(forKey: peerID)

                // Stop advertising once connected
                if isAdvertising {
                    stopAdvertising()
                }

                isConnected = true
                print("Conexión MultipeerConnectivity aceptada para: \(endpointId)")
                delegate?.onSuccess()
            } else {
                print("No hay invitación pendiente o MCSession es nil para: \(endpointId)")
                delegate?.onSuccess()
            }
        }
    }

    public func rejectConnection(endpointId: String) {
        // Check if this is a Bluetooth connection (Android device)
        if let peripheral = connectedPeripherals[endpointId] {
            // Disconnect from the peripheral
            centralManager?.cancelPeripheralConnection(peripheral)
            connectedPeripherals.removeValue(forKey: endpointId)

            print("Conexión Bluetooth rechazada para: \(endpointId)")
            delegate?.onSuccess()
        } else {
            // For MultipeerConnectivity, we need to reject the invitation
            // Find the invitation handler for this peer
            if let peerID = getPeerByEndpointId(endpointId),
               let invitationHandler = invitationHandlers[peerID],
               let session = session {
                // Reject the invitation
                invitationHandler(false, session)
                invitationHandlers.removeValue(forKey: peerID)

                print("Conexión MultipeerConnectivity rechazada para: \(endpointId)")
                delegate?.onSuccess()
            } else {
                print("No hay invitación pendiente o MCSession es nil para: \(endpointId)")
                delegate?.onSuccess()
            }
        }
    }

    public func disconnectFromEndpoint(endpointId: String) {
        // Check if this is a Bluetooth connection (Android device)
        if let peripheral = connectedPeripherals[endpointId] {
            // Disconnect from the peripheral
            centralManager?.cancelPeripheralConnection(peripheral)
            connectedPeripherals.removeValue(forKey: endpointId)

            // Resume advertising after disconnection
            if !isAdvertising && !isConnected {
                startBluetoothAdvertising()
                isAdvertising = true
            }

            print("Desconectado del dispositivo Bluetooth: \(endpointId)")

            // Notify disconnection
            DispatchQueue.main.async {
                self.delegate?.onEndpointLost(endpointId: endpointId)
            }
        } else if let peerToDisconnect = getPeerByEndpointId(endpointId), let session = session {
            // This is a MultipeerConnectivity connection (iOS device)
            let connectedPeers = session.connectedPeers
            if connectedPeers.contains(peerToDisconnect) {
                // Crear diccionario para el motivo
                let reason = ["reason": "Disconnected by user"]

                // Convertir a Data
                if let data = try? JSONSerialization.data(withJSONObject: reason) {
                    try? session.send(data, toPeers: [peerToDisconnect], with: .reliable)
                }

                // Resume advertising after disconnection
                if !isAdvertising && !isConnected {
                    startAdvertising()
                }
            }
            print("Desconexión solicitada para: \(endpointId)")
        } else {
            print("Endpoint no encontrado o no conectado: \(endpointId)")
        }
    }

    public func disconnectFromAllEndpoints() {
        // Disconnect all Bluetooth connections
        for (endpointId, peripheral) in connectedPeripherals {
            centralManager?.cancelPeripheralConnection(peripheral)
            print("Desconectado del dispositivo Bluetooth: \(endpointId)")
        }
        connectedPeripherals.removeAll()

        // Disconnect all MultipeerConnectivity connections
        session?.disconnect()

        // Resume advertising after disconnection
        if !isAdvertising && !isConnected {
            startAdvertising()
        }

        print("Desconectado de todos los endpoints")
    }

    public func sendMessage(endpointId: String, message: String) {
        // Check if this is a Bluetooth connection (Android device)
        if let peripheral = connectedPeripherals[endpointId],
           let characteristic = transferCharacteristic {

            // Convert message to data
            guard let data = message.data(using: .utf8) else {
                delegate?.onError(error: "Error al convertir mensaje a datos")
                return
            }

            // Send data via Bluetooth
            peripheral.writeValue(data, for: characteristic, type: .withResponse)
            print("Mensaje Bluetooth enviado a \(endpointId): \(message)")

            // Notify payload transfer update
            DispatchQueue.main.async {
                self.delegate?.onPayloadTransferUpdate(
                    endpointId: endpointId,
                    bytesTransferred: Int64(data.count),
                    totalBytes: Int64(data.count),
                    completed: true
                )
            }

            delegate?.onSuccess()
        } else {
            // This is a MultipeerConnectivity connection (iOS device)
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
                        print("Mensaje MultipeerConnectivity enviado a \(endpointId): \(message)")
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
    }

    public func cleanup() {
        stopAdvertising()
        stopDiscovery()

        // Disconnect all Bluetooth connections
        for (_, peripheral) in connectedPeripherals {
            centralManager?.cancelPeripheralConnection(peripheral)
        }
        connectedPeripherals.removeAll()

        // Disconnect MultipeerConnectivity session
        session?.disconnect()
        session = nil
        peerID = nil

        isAdvertising = false
        isDiscovering = false
        isConnected = false

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

    // MARK: - Utilidades de UUID BLE
    static func formatBleUuid(_ uuid: String) -> String {
        var uuid = uuid.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if uuid.hasPrefix("0x") {
            uuid = String(uuid.dropFirst(2))
        }
        if uuid.count < 4 {
            fatalError("UUID inválido")
        }
        if uuid.count <= 8 {
            uuid = uuid.paddingLeft(toLength: 8, withPad: "0") + "-0000-1000-8000-00805f9b34fb"
        }
        if !uuid.contains("-") {
            guard uuid.count == 32 else { fatalError("UUID inválido") }
            uuid = "\(uuid.prefix(8))-\(uuid.dropFirst(8).prefix(4))-\(uuid.dropFirst(12).prefix(4))-\(uuid.dropFirst(16).prefix(4))-\(uuid.dropFirst(20))"
        }
        let groups = uuid.split(separator: "-")
        guard groups.count == 5,
              groups[0].count == 8,
              groups[1].count == 4,
              groups[2].count == 4,
              groups[3].count == 4,
              groups[4].count == 12,
              groups.allSatisfy({ $0.range(of: "^[0-9a-f]+$", options: .regularExpression) != nil })
        else { fatalError("UUID inválido") }
        return uuid
    }
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

// MARK: - CBCentralManagerDelegate

extension NearbyMultipeer: CBCentralManagerDelegate {
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            print("Bluetooth central está encendido")
            // Restart discovery if needed
            if isDiscovering {
                startBluetoothDiscovery()
            }
        case .poweredOff:
            print("Bluetooth central está apagado")
        case .resetting:
            print("Bluetooth central está reiniciando")
        case .unauthorized:
            print("Bluetooth central no está autorizado")
        case .unsupported:
            print("Bluetooth central no es soportado")
        case .unknown:
            print("Estado de Bluetooth central desconocido")
        @unknown default:
            print("Estado de Bluetooth central desconocido (default)")
        }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        // Loguear todos los dispositivos encontrados
        let name = peripheral.name ?? advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? "Unknown"
        print("Peripheral encontrado: \(name)")
        print("UUID: \(peripheral.identifier.uuidString)")
        print("AdvertisementData: \(advertisementData)")
        print("RSSI: \(RSSI)")

        // Detectar Android por UUID de servicio y notificar a JS/TS automáticamente
        if let serviceUUIDs = advertisementData["kCBAdvDataServiceUUIDs"] as? [CBUUID] {
            for uuid in serviceUUIDs {
                print("UUID de servicio encontrado: \(uuid.uuidString)")
                if uuid.uuidString.uppercased() == serviceUUID.uuidString.uppercased() {
                    print("¡Dispositivo Android detectado por UUID de servicio!")
                    let endpointId = peripheral.identifier.uuidString
                    discoveredPeripherals[endpointId] = peripheral
                    DispatchQueue.main.async {
                        self.delegate?.onEndpointFound(
                            endpointId: endpointId,
                            endpointName: name,
                            serviceId: self.serviceId
                        )
                    }
                }
            }
        }
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let endpointId = peripheral.identifier.uuidString
        print("Conectado a dispositivo Bluetooth: \(peripheral.name ?? endpointId)")

        // Store the connected peripheral
        connectedPeripherals[endpointId] = peripheral

        // Discover services
        peripheral.discoverServices([NearbyMultipeer.SERVICE_UUID])

        // Set connected flag
        isConnected = true

        // Notify connection result
        DispatchQueue.main.async {
            self.delegate?.onConnectionResult(endpointId: endpointId, connected: true)
        }
    }

    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        let endpointId = peripheral.identifier.uuidString
        print("Error al conectar con dispositivo Bluetooth: \(peripheral.name ?? endpointId), error: \(error?.localizedDescription ?? "unknown")")

        // Notify connection result
        DispatchQueue.main.async {
            self.delegate?.onConnectionResult(endpointId: endpointId, connected: false)
        }
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let endpointId = peripheral.identifier.uuidString
        print("Desconectado de dispositivo Bluetooth: \(peripheral.name ?? endpointId)")

        // Remove from connected peripherals
        connectedPeripherals.removeValue(forKey: endpointId)

        // Update connected flag
        if connectedPeripherals.isEmpty {
            isConnected = false

            // Resume advertising if needed
            if !isAdvertising {
                startBluetoothAdvertising()
                isAdvertising = true
            }
        }

        // Notify disconnection
        DispatchQueue.main.async {
            self.delegate?.onEndpointLost(endpointId: endpointId)
        }
    }
}

// MARK: - CBPeripheralDelegate

extension NearbyMultipeer: CBPeripheralDelegate {
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            print("Error al descubrir servicios: \(error.localizedDescription)")
            return
        }

        guard let services = peripheral.services else { return }

        for service in services {
            print("Servicio descubierto: \(service.uuid)")
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error = error {
            print("Error al descubrir características: \(error.localizedDescription)")
            return
        }

        guard let characteristics = service.characteristics else { return }

        for characteristic in characteristics {
            print("Característica descubierta: \(characteristic.uuid)")

            if characteristic.uuid == NearbyMultipeer.CHARACTERISTIC_UUID {
                // Store the characteristic for later use
                transferCharacteristic = characteristic

                // Subscribe to notifications
                peripheral.setNotifyValue(true, for: characteristic)
            }
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            print("Error al recibir datos: \(error.localizedDescription)")
            return
        }

        guard let data = characteristic.value else { return }

        // Convert data to string
        if let message = String(data: data, encoding: .utf8) {
            let endpointId = peripheral.identifier.uuidString
            print("Mensaje recibido de \(peripheral.name ?? endpointId): \(message)")

            // Notify the plugin
            DispatchQueue.main.async {
                self.delegate?.onMessageReceived(endpointId: endpointId, data: message)
            }
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            print("Error al enviar datos: \(error.localizedDescription)")
            return
        }

        print("Datos enviados correctamente a \(peripheral.name ?? peripheral.identifier.uuidString)")
    }
}

// MARK: - CBPeripheralManagerDelegate

extension NearbyMultipeer: CBPeripheralManagerDelegate {
    public func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            print("Bluetooth peripheral está encendido")
            // Restart advertising if needed
            if isAdvertising {
                startBluetoothAdvertising()
            }
        case .poweredOff:
            print("Bluetooth peripheral está apagado")
        case .resetting:
            print("Bluetooth peripheral está reiniciando")
        case .unauthorized:
            print("Bluetooth peripheral no está autorizado")
        case .unsupported:
            print("Bluetooth peripheral no es soportado")
        case .unknown:
            print("Estado de Bluetooth peripheral desconocido")
        @unknown default:
            print("Estado de Bluetooth peripheral desconocido (default)")
        }
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            print("Error al añadir servicio: \(error.localizedDescription)")
            return
        }

        print("Servicio añadido correctamente")
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        // Respond with the device name
        if request.characteristic.uuid == NearbyMultipeer.CHARACTERISTIC_UUID {
            if let data = deviceName.data(using: .utf8) {
                request.value = data
                peripheral.respond(to: request, withResult: .success)
            } else {
                peripheral.respond(to: request, withResult: .unlikelyError)
            }
        } else {
            peripheral.respond(to: request, withResult: .attributeNotFound)
        }
    }

    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            if request.characteristic.uuid == NearbyMultipeer.CHARACTERISTIC_UUID,
               let data = request.value,
               let message = String(data: data, encoding: .utf8) {

                print("Mensaje recibido: \(message)")

                // Notify the plugin
                let endpointId = request.central.identifier.uuidString
                DispatchQueue.main.async {
                    self.delegate?.onMessageReceived(endpointId: endpointId, data: message)
                }

                peripheral.respond(to: request, withResult: .success)
            } else {
                peripheral.respond(to: request, withResult: .requestNotSupported)
            }
        }
    }
}

// Extensión para padding de strings
extension String {
    func paddingLeft(toLength: Int, withPad character: Character) -> String {
        let padCount = toLength - self.count
        guard padCount > 0 else { return self }
        return String(repeating: character, count: padCount) + self
    }
}
