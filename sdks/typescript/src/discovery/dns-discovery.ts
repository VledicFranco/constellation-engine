/**
 * Resolves a DNS service name to one or more addresses.
 *
 * Mirrors the Scala DnsDiscovery case class.
 */

import { promises as dns } from "node:dns";
import type { DiscoveryStrategy } from "./discovery-strategy.js";

export class DnsDiscovery implements DiscoveryStrategy {
  private readonly serviceName: string;
  private readonly port: number;

  constructor(serviceName: string, port = 9090) {
    this.serviceName = serviceName;
    this.port = port;
  }

  async instances(): Promise<string[]> {
    const addresses = await dns.resolve4(this.serviceName);
    return addresses.map((addr) => `${addr}:${this.port}`);
  }
}
