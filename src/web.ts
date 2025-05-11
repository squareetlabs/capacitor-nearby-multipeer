import { WebPlugin } from '@capacitor/core';

import type { NearbyMultipeerPlugin } from './definitions';

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
}
