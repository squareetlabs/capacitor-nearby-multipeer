import type { PluginListenerHandle } from '@capacitor/core';
import { WebPlugin } from '@capacitor/core';

import type {
  NearbyMultipeerPlugin,
  ConnectionRequestEvent,
  ConnectionResultEvent,
  EndpointFoundEvent,
  EndpointLostEvent,
  MessageReceivedEvent,
  PayloadTransferUpdateEvent
} from './definitions';

export class NearbyMultipeerWeb
  extends WebPlugin
  implements NearbyMultipeerPlugin
{
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async initialize(options: { serviceId: string }): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API', options);
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async setStrategy(options: { strategy: string }): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API', options);
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async startAdvertising(options: { displayName?: string }): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API', options);
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async stopAdvertising(): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API');
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async startDiscovery(): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API');
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async stopDiscovery(): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API');
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async connect(options: { endpointId: string, displayName?: string }): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API', options);
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async acceptConnection(options: { endpointId: string }): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API', options);
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async rejectConnection(options: { endpointId: string }): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API', options);
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async disconnectFromEndpoint(options: { endpointId: string }): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API', options);
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async disconnect(): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API');
    throw this.unavailable('Nearby Connections API not available on web');
  }

  async sendMessage(options: { endpointId: string, data: string }): Promise<void> {
    console.log('Web implementation not available for Nearby Connections API', options);
    throw this.unavailable('Nearby Connections API not available on web');
  }

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
  addListener(
    eventName: string,
    listenerFunc: (event: any) => void
  ): Promise<PluginListenerHandle> & PluginListenerHandle {
    const listener = super.addListener(eventName, listenerFunc);
    return Object.assign(listener, {
      remove: () => {
        this.removeAllListeners();
        return Promise.resolve();
      }
    });
  }

  async removeAllListeners(): Promise<void> {
    await super.removeAllListeners();
  }
}
