import { registerPlugin } from '@capacitor/core';

import type { NearbyMultipeerPlugin } from './definitions';

const NearbyMultipeer = registerPlugin<NearbyMultipeerPlugin>(
  'NearbyMultipeer',
  {
    web: () => import('./web').then(m => new m.NearbyMultipeerWeb()),
  },
);

export * from './definitions';
export { NearbyMultipeer };
