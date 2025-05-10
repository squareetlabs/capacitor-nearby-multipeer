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
}
