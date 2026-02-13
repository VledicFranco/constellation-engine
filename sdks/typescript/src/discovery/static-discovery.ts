/**
 * Returns a fixed list of addresses.
 *
 * Mirrors the Scala StaticDiscovery case class.
 */

import type { DiscoveryStrategy } from "./discovery-strategy.js";

export class StaticDiscovery implements DiscoveryStrategy {
  private readonly addresses: string[];

  constructor(addresses: string[]) {
    this.addresses = [...addresses];
  }

  async instances(): Promise<string[]> {
    return [...this.addresses];
  }
}
