import type { PluginListenerHandle } from '@capacitor/core';

export interface NearbyMultipeerPlugin {
  /**
   * Método de prueba para verificar que el plugin funciona
   */
  echo(options: { value: string }): Promise<{ value: string }>;

  /**
   * Inicializa el plugin con el identificador de servicio y UUID opcional
   * @param options Opciones de inicialización
   * @param options.serviceId Identificador lógico del servicio (obligatorio)
   * @param options.serviceUUIDString UUID BLE personalizado (opcional, por defecto: 'fa87c0d0-afac-11de-8a39-0800200c9a66').
   *        Debe ser igual en Android e iOS para que ambos sistemas puedan descubrirse por BLE.
   */
  initialize(options: { serviceId: string; serviceUUIDString?: string }): Promise<void>;

  /**
   * Configura la estrategia de conexión a usar
   * @param options Estrategia a usar ("P2P_STAR", "P2P_CLUSTER", "P2P_POINT_TO_POINT")
   */
  setStrategy(options: { strategy: string }): Promise<void>;

  /**
   * Comienza a anunciar el dispositivo para que otros puedan encontrarlo
   * @param options Opciones de publicidad
   */
  startAdvertising(options: { displayName?: string }): Promise<void>;

  /**
   * Detiene la publicidad del dispositivo
   */
  stopAdvertising(): Promise<void>;

  /**
   * Comienza a buscar dispositivos cercanos
   */
  startDiscovery(): Promise<void>;

  /**
   * Detiene la búsqueda de dispositivos cercanos
   */
  stopDiscovery(): Promise<void>;

  /**
   * Solicita una conexión a un endpoint encontrado
   * @param options Opciones de conexión
   */
  connect(options: { endpointId: string, displayName?: string }): Promise<void>;

  /**
   * Acepta una solicitud de conexión entrante
   * @param options ID del endpoint que solicita la conexión
   */
  acceptConnection(options: { endpointId: string }): Promise<void>;

  /**
   * Rechaza una solicitud de conexión entrante
   * @param options ID del endpoint que solicita la conexión
   */
  rejectConnection(options: { endpointId: string }): Promise<void>;

  /**
   * Desconecta de un endpoint específico
   * @param options ID del endpoint a desconectar
   */
  disconnectFromEndpoint(options: { endpointId: string }): Promise<void>;

  /**
   * Desconecta de todos los endpoints
   */
  disconnect(): Promise<void>;

  /**
   * Envía un mensaje a un endpoint conectado
   * @param options Opciones del mensaje
   */
  sendMessage(options: { endpointId: string, data: string }): Promise<void>;

  /**
   * Establece el nivel de logs del plugin
   * @param options Opciones de configuración de logs
   * @param options.logLevel Nivel de logs (0=ninguno, 1=error, 2=warn, 3=info, 4=debug, 5=verbose)
   */
  setLogLevel(options: { logLevel: number }): Promise<void>;

  /**
   * Agrega un listener para un evento específico
   * @param eventName Nombre del evento
   * @param listenerFunc Función que maneja el evento
   */
  addListener(
    eventName: 'connectionRequested',
    listenerFunc: (event: ConnectionRequestEvent) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
  addListener(
    eventName: 'connectionResult',
    listenerFunc: (event: ConnectionResultEvent) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
  addListener(
    eventName: 'endpointFound',
    listenerFunc: (event: EndpointFoundEvent) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
  addListener(
    eventName: 'endpointLost',
    listenerFunc: (event: EndpointLostEvent) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
  addListener(
    eventName: 'message',
    listenerFunc: (event: MessageReceivedEvent) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
  addListener(
    eventName: 'payloadTransferUpdate',
    listenerFunc: (event: PayloadTransferUpdateEvent) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Remueve todos los listeners registrados
   */
  removeAllListeners(): Promise<void>;
}

/**
 * Eventos emitidos por el plugin
 */
export interface NearbyMultipeerEvents {
  /**
   * Disparado cuando se recibe una solicitud de conexión
   */
  connectionRequested: ConnectionRequestEvent;

  /**
   * Disparado cuando hay un resultado de conexión
   */
  connectionResult: ConnectionResultEvent;

  /**
   * Disparado cuando se encuentra un nuevo endpoint
   */
  endpointFound: EndpointFoundEvent;

  /**
   * Disparado cuando se pierde un endpoint
   */
  endpointLost: EndpointLostEvent;

  /**
   * Disparado cuando se recibe un mensaje
   */
  message: MessageReceivedEvent;

  /**
   * Disparado durante la transferencia de un payload
   */
  payloadTransferUpdate: PayloadTransferUpdateEvent;
}

/**
 * Evento cuando se recibe una solicitud de conexión
 */
export interface ConnectionRequestEvent {
  /**
   * ID del endpoint que solicita la conexión
   */
  endpointId: string;

  /**
   * Nombre del endpoint
   */
  endpointName: string;

  /**
   * Token de autenticación para verificar la identidad
   */
  authenticationToken: string;

  /**
   * Si es una conexión entrante o saliente
   */
  isIncomingConnection: boolean;
}

/**
 * Evento con el resultado de una conexión
 */
export interface ConnectionResultEvent {
  /**
   * ID del endpoint
   */
  endpointId: string;

  /**
   * Código de estado de la conexión
   * 0 = éxito
   * -1 = error
   */
  status: number;
}

/**
 * Evento cuando se encuentra un nuevo endpoint
 */
export interface EndpointFoundEvent {
  /**
   * ID del endpoint encontrado
   */
  endpointId: string;

  /**
   * Nombre del endpoint
   */
  endpointName: string;

  /**
   * ID del servicio del endpoint
   */
  serviceId: string;
}

/**
 * Evento cuando se pierde un endpoint
 */
export interface EndpointLostEvent {
  /**
   * ID del endpoint perdido
   */
  endpointId: string;
}

/**
 * Evento cuando se recibe un mensaje
 */
export interface MessageReceivedEvent {
  /**
   * ID del endpoint que envió el mensaje
   */
  endpointId: string;

  /**
   * Datos del mensaje
   */
  data: string;
}

/**
 * Evento con información de progreso de transferencia
 */
export interface PayloadTransferUpdateEvent {
  /**
   * ID del endpoint
   */
  endpointId: string;

  /**
   * Bytes transferidos
   */
  bytesTransferred: number;

  /**
   * Total de bytes a transferir
   */
  totalBytes: number;

  /**
   * Estado de la transferencia
   * 2 = en progreso
   * 3 = completado
   */
  status: number;
}
