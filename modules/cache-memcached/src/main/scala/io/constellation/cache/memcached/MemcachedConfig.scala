package io.constellation.cache.memcached

import scala.concurrent.duration.*

/** Configuration for connecting to a Memcached cluster.
  *
  * @param addresses
  *   Memcached server addresses in `host:port` format
  * @param operationTimeout
  *   Timeout for individual cache operations
  * @param connectionTimeout
  *   Reserved for future use. Spymemcached does not expose a dedicated connection timeout setting;
  *   the `operationTimeout` governs blocking behavior for all operations including initial connect.
  * @param maxReconnectDelay
  *   Maximum delay between reconnection attempts (in seconds)
  * @param keyPrefix
  *   Optional prefix prepended to all cache keys (useful for multi-tenant setups)
  */
final case class MemcachedConfig(
    addresses: List[String] = List("localhost:11211"),
    operationTimeout: FiniteDuration = 2500.millis,
    connectionTimeout: FiniteDuration = 5.seconds,
    maxReconnectDelay: FiniteDuration = 30.seconds,
    keyPrefix: String = ""
)

object MemcachedConfig {

  /** Create a config for a single Memcached server.
    *
    * @param address
    *   Server address in `host:port` format (default: `localhost:11211`)
    */
  def single(address: String = "localhost:11211"): MemcachedConfig =
    MemcachedConfig(addresses = List(address))

  /** Create a config for a Memcached cluster.
    *
    * @param addresses
    *   Server addresses in `host:port` format
    */
  def cluster(addresses: String*): MemcachedConfig =
    MemcachedConfig(addresses = addresses.toList)
}
