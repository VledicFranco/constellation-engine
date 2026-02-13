/**
 * Strategy for discovering Constellation server instances to connect to.
 *
 * Mirrors the Scala DiscoveryStrategy trait.
 */

export interface DiscoveryStrategy {
  instances(): Promise<string[]>;
}
