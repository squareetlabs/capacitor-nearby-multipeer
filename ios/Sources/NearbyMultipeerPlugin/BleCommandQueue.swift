import Foundation

/**
 * Cola de comandos BLE para evitar operaciones concurrentes que pueden causar fallos
 */
class BleCommandQueue {
    private var commandQueue: [() -> Void] = []
    private var isExecuting = false
    private var defaultTimeout: TimeInterval = 10.0 // 10 segundos por defecto
    
    /**
     * Encola un comando para ejecución secuencial con timeout específico
     * - Parameters:
     *   - command: Comando a ejecutar
     *   - timeout: Timeout opcional en segundos
     * - Returns: Resultado del comando
     */
    func enqueue<T>(_ command: @escaping () -> T, timeout: TimeInterval? = nil) -> T {
        let semaphore = DispatchSemaphore(value: 0)
        var result: T!
        var capturedError: Error?
        
        // Encolar el comando
        commandQueue.append {
            do {
                // Ejecutar con timeout
                let timeoutHandler = DispatchWorkItem {
                    capturedError = NSError(domain: "BleCommandQueue", code: -1, userInfo: [
                        NSLocalizedDescriptionKey: "Comando expiró después de \(timeout ?? self.defaultTimeout) segundos"
                    ])
                    semaphore.signal()
                }
                
                // Ejecutar el comando en el hilo principal
                DispatchQueue.main.async {
                    result = command()
                    // Cancelar el timeout
                    timeoutHandler.cancel()
                    semaphore.signal()
                }
                
                // Configurar timeout si está especificado
                if let timeout = timeout, timeout > 0 {
                    DispatchQueue.global().asyncAfter(deadline: .now() + timeout, execute: timeoutHandler)
                } else if self.defaultTimeout > 0 {
                    DispatchQueue.global().asyncAfter(deadline: .now() + self.defaultTimeout, execute: timeoutHandler)
                }
            } catch {
                capturedError = error
                semaphore.signal()
            }
        }
        
        // Iniciar la ejecución si no hay otros comandos en proceso
        if !isExecuting {
            executeNext()
        }
        
        // Esperar a que se complete el comando
        _ = semaphore.wait(timeout: .distantFuture)
        
        // Verificar si hubo error durante la ejecución
        if let error = capturedError {
            print("Error ejecutando comando BLE: \(error.localizedDescription)")
            // No podemos lanzar excepciones dentro de un closure en Swift, así que imprimimos el error
            // y retornamos el valor por defecto para evitar un crash
        }
        
        return result
    }
    
    /**
     * Encola un comando sin retorno
     * - Parameter command: Comando a ejecutar
     */
    func enqueueVoid(_ command: @escaping () -> Void) {
        enqueue {
            command()
            return ()
        }
    }
    
    /**
     * Encola un comando que retorna una promesa
     * - Parameters:
     *   - command: Comando a ejecutar que retorna una promesa
     *   - timeout: Timeout opcional en segundos
     * - Returns: Promesa con el resultado del comando
     */
    func enqueueAsync<T>(_ command: @escaping () -> Promise<T>, timeout: TimeInterval? = nil) -> Promise<T> {
        let promise = Promise<T>()
        
        enqueueVoid {
            let innerPromise = command()
            innerPromise.then { result in
                promise.resolve(result)
            }.catch { error in
                promise.reject(error)
            }
        }
        
        return promise
    }
    
    private func executeNext() {
        guard !commandQueue.isEmpty else {
            isExecuting = false
            return
        }
        
        isExecuting = true
        let command = commandQueue.removeFirst()
        command()
        
        // Ejecutar el siguiente comando
        DispatchQueue.main.async { [weak self] in
            self?.executeNext()
        }
    }
    
    /**
     * Establece el timeout por defecto para todos los comandos
     * - Parameter timeout: Timeout en segundos (0 para desactivar)
     */
    func setDefaultTimeout(_ timeout: TimeInterval) {
        defaultTimeout = timeout
    }
}

/**
 * Clase simple de Promise para manejar operaciones asíncronas
 */
class Promise<T> {
    typealias ResolveCallback = (T) -> Void
    typealias RejectCallback = (Error) -> Void
    
    private var resolveCallbacks: [ResolveCallback] = []
    private var rejectCallbacks: [RejectCallback] = []
    private var value: T?
    private var error: Error?
    private var isResolved = false
    private var isRejected = false
    
    func resolve(_ value: T) {
        guard !isResolved && !isRejected else { return }
        
        self.value = value
        isResolved = true
        
        resolveCallbacks.forEach { $0(value) }
        resolveCallbacks.removeAll()
        rejectCallbacks.removeAll()
    }
    
    func reject(_ error: Error) {
        guard !isResolved && !isRejected else { return }
        
        self.error = error
        isRejected = true
        
        rejectCallbacks.forEach { $0(error) }
        resolveCallbacks.removeAll()
        rejectCallbacks.removeAll()
    }
    
    @discardableResult
    func then(_ callback: @escaping ResolveCallback) -> Promise<T> {
        if isResolved, let value = value {
            callback(value)
        } else {
            resolveCallbacks.append(callback)
        }
        
        return self
    }
    
    @discardableResult
    func `catch`(_ callback: @escaping RejectCallback) -> Promise<T> {
        if isRejected, let error = error {
            callback(error)
        } else {
            rejectCallbacks.append(callback)
        }
        
        return self
    }
} 