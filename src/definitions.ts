export interface NearbyMultipeerPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
