package io.constellation.provider.sdk

import java.net.InetAddress

import cats.effect.IO

/** Strategy for discovering Constellation server instances to connect to. */
trait DiscoveryStrategy {
  def instances: IO[List[String]]
}

/** Returns a fixed list of addresses. */
final case class StaticDiscovery(addresses: List[String]) extends DiscoveryStrategy {
  def instances: IO[List[String]] = IO.pure(addresses)
}

/** Resolves a DNS service name to one or more addresses. */
final case class DnsDiscovery(serviceName: String, port: Int = 9090) extends DiscoveryStrategy {
  def instances: IO[List[String]] = IO {
    val addresses = InetAddress.getAllByName(serviceName)
    addresses.map(addr => s"${addr.getHostAddress}:$port").toList
  }
}
