package io.constellation.provider.sdk

import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DiscoveryStrategySpec extends AnyFlatSpec with Matchers {

  // ===== StaticDiscovery =====

  "StaticDiscovery" should "return configured addresses" in {
    val disc = StaticDiscovery(List("host1:9090", "host2:9090"))

    val result = disc.instances.unsafeRunSync()
    result shouldBe List("host1:9090", "host2:9090")
  }

  it should "return empty list when no addresses configured" in {
    val disc = StaticDiscovery(List.empty)

    val result = disc.instances.unsafeRunSync()
    result shouldBe empty
  }

  it should "preserve address order" in {
    val disc = StaticDiscovery(List("c:9090", "a:9090", "b:9090"))

    val result = disc.instances.unsafeRunSync()
    result shouldBe List("c:9090", "a:9090", "b:9090")
  }

  // ===== DnsDiscovery =====

  "DnsDiscovery" should "resolve localhost to at least one address" in {
    val disc = DnsDiscovery("localhost", 9090)

    val result = disc.instances.unsafeRunSync()
    result should not be empty
    result.foreach(_ should include(":9090"))
  }
}
