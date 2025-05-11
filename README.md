# @squareetlabs/capacitor-nearby-multipeer

Capacitor plugin for Google Nearby & iOS Multipeer Connectivity

## Install

```bash
npm install @squareetlabs/capacitor-nearby-multipeer
npx cap sync
```

## API

<docgen-index>

* [`echo(...)`](#echo)
* [`initialize(...)`](#initialize)
* [`setStrategy(...)`](#setstrategy)
* [`startAdvertising(...)`](#startadvertising)
* [`stopAdvertising()`](#stopadvertising)
* [`startDiscovery()`](#startdiscovery)
* [`stopDiscovery()`](#stopdiscovery)
* [`connect(...)`](#connect)
* [`acceptConnection(...)`](#acceptconnection)
* [`rejectConnection(...)`](#rejectconnection)
* [`disconnectFromEndpoint(...)`](#disconnectfromendpoint)
* [`disconnect()`](#disconnect)
* [`sendMessage(...)`](#sendmessage)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

Método de prueba para verificar que el plugin funciona

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------


### initialize(...)

```typescript
initialize(options: { serviceId: string; }) => Promise<void>
```

Inicializa el plugin con el identificador de servicio

| Param         | Type                                | Description                |
| ------------- | ----------------------------------- | -------------------------- |
| **`options`** | <code>{ serviceId: string; }</code> | Opciones de inicialización |

--------------------


### setStrategy(...)

```typescript
setStrategy(options: { strategy: string; }) => Promise<void>
```

Configura la estrategia de conexión a usar

| Param         | Type                               | Description                                                         |
| ------------- | ---------------------------------- | ------------------------------------------------------------------- |
| **`options`** | <code>{ strategy: string; }</code> | Estrategia a usar ("P2P_STAR", "P2P_CLUSTER", "P2P_POINT_TO_POINT") |

--------------------


### startAdvertising(...)

```typescript
startAdvertising(options: { displayName?: string; }) => Promise<void>
```

Comienza a anunciar el dispositivo para que otros puedan encontrarlo

| Param         | Type                                   | Description            |
| ------------- | -------------------------------------- | ---------------------- |
| **`options`** | <code>{ displayName?: string; }</code> | Opciones de publicidad |

--------------------


### stopAdvertising()

```typescript
stopAdvertising() => Promise<void>
```

Detiene la publicidad del dispositivo

--------------------


### startDiscovery()

```typescript
startDiscovery() => Promise<void>
```

Comienza a buscar dispositivos cercanos

--------------------


### stopDiscovery()

```typescript
stopDiscovery() => Promise<void>
```

Detiene la búsqueda de dispositivos cercanos

--------------------


### connect(...)

```typescript
connect(options: { endpointId: string; displayName?: string; }) => Promise<void>
```

Solicita una conexión a un endpoint encontrado

| Param         | Type                                                       | Description          |
| ------------- | ---------------------------------------------------------- | -------------------- |
| **`options`** | <code>{ endpointId: string; displayName?: string; }</code> | Opciones de conexión |

--------------------


### acceptConnection(...)

```typescript
acceptConnection(options: { endpointId: string; }) => Promise<void>
```

Acepta una solicitud de conexión entrante

| Param         | Type                                 | Description                              |
| ------------- | ------------------------------------ | ---------------------------------------- |
| **`options`** | <code>{ endpointId: string; }</code> | ID del endpoint que solicita la conexión |

--------------------


### rejectConnection(...)

```typescript
rejectConnection(options: { endpointId: string; }) => Promise<void>
```

Rechaza una solicitud de conexión entrante

| Param         | Type                                 | Description                              |
| ------------- | ------------------------------------ | ---------------------------------------- |
| **`options`** | <code>{ endpointId: string; }</code> | ID del endpoint que solicita la conexión |

--------------------


### disconnectFromEndpoint(...)

```typescript
disconnectFromEndpoint(options: { endpointId: string; }) => Promise<void>
```

Desconecta de un endpoint específico

| Param         | Type                                 | Description                   |
| ------------- | ------------------------------------ | ----------------------------- |
| **`options`** | <code>{ endpointId: string; }</code> | ID del endpoint a desconectar |

--------------------


### disconnect()

```typescript
disconnect() => Promise<void>
```

Desconecta de todos los endpoints

--------------------


### sendMessage(...)

```typescript
sendMessage(options: { endpointId: string; data: string; }) => Promise<void>
```

Envía un mensaje a un endpoint conectado

| Param         | Type                                               | Description          |
| ------------- | -------------------------------------------------- | -------------------- |
| **`options`** | <code>{ endpointId: string; data: string; }</code> | Opciones del mensaje |

--------------------

</docgen-api>
