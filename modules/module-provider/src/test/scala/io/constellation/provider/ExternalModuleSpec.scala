package io.constellation.provider

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExternalModuleSpec extends AnyFlatSpec with Matchers {

  // ===== parseHostPort =====

  "ExternalModule.parseHostPort" should "parse host:port" in {
    ExternalModule.parseHostPort("localhost:9090") shouldBe Right(("localhost", 9090))
  }

  it should "parse hostname without port (default 9090)" in {
    ExternalModule.parseHostPort("myhost") shouldBe Right(("myhost", 9090))
  }

  it should "parse FQDN with port" in {
    ExternalModule.parseHostPort("example.com:8080") shouldBe Right(("example.com", 8080))
  }

  it should "parse IPv6 address with port" in {
    ExternalModule.parseHostPort("[::1]:9090") shouldBe Right(("[::1]", 9090))
  }

  it should "parse IPv6 address without port (default 9090)" in {
    ExternalModule.parseHostPort("[::1]") shouldBe Right(("[::1]", 9090))
  }

  it should "reject empty URL" in {
    ExternalModule.parseHostPort("") shouldBe a[Left[_, _]]
    ExternalModule.parseHostPort("   ") shouldBe a[Left[_, _]]
  }

  it should "reject URL with scheme prefix" in {
    val result = ExternalModule.parseHostPort("http://localhost:9090")
    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("scheme")
  }

  it should "reject URL with grpc scheme prefix" in {
    ExternalModule.parseHostPort("grpc://host:9090") shouldBe a[Left[_, _]]
  }

  it should "reject invalid port (non-numeric)" in {
    ExternalModule.parseHostPort("localhost:abc") shouldBe a[Left[_, _]]
  }

  it should "reject port out of range (0)" in {
    ExternalModule.parseHostPort("localhost:0") shouldBe a[Left[_, _]]
  }

  it should "reject port out of range (70000)" in {
    ExternalModule.parseHostPort("localhost:70000") shouldBe a[Left[_, _]]
  }

  it should "accept port at boundary (1)" in {
    ExternalModule.parseHostPort("localhost:1") shouldBe Right(("localhost", 1))
  }

  it should "accept port at boundary (65535)" in {
    ExternalModule.parseHostPort("localhost:65535") shouldBe Right(("localhost", 65535))
  }

  it should "reject empty host with port" in {
    ExternalModule.parseHostPort(":9090") shouldBe a[Left[_, _]]
  }

  it should "reject malformed IPv6" in {
    ExternalModule.parseHostPort("[::1") shouldBe a[Left[_, _]]
  }

  it should "reject IPv6 with trailing garbage" in {
    ExternalModule.parseHostPort("[::1]garbage") shouldBe a[Left[_, _]]
  }

  it should "trim whitespace" in {
    ExternalModule.parseHostPort("  localhost:9090  ") shouldBe Right(("localhost", 9090))
  }

  // ===== GrpcChannelCache =====

  "GrpcChannelCache" should "return same channel for same URL" in {
    val cache = new GrpcChannelCache
    try {
      val ch1 = cache.getChannel("localhost:50051")
      val ch2 = cache.getChannel("localhost:50051")
      ch1 shouldBe theSameInstanceAs(ch2)
    } finally cache.shutdownAll()
  }

  it should "return different channels for different URLs" in {
    val cache = new GrpcChannelCache
    try {
      val ch1 = cache.getChannel("localhost:50051")
      val ch2 = cache.getChannel("localhost:50052")
      ch1 should not be theSameInstanceAs(ch2)
    } finally cache.shutdownAll()
  }

  it should "remove channel on shutdownChannel" in {
    val cache = new GrpcChannelCache
    try {
      val ch1 = cache.getChannel("localhost:50051")
      cache.shutdownChannel("localhost:50051")
      // Getting the same URL should create a new channel
      val ch2 = cache.getChannel("localhost:50051")
      ch1 should not be theSameInstanceAs(ch2)
    } finally cache.shutdownAll()
  }

  it should "handle shutdownChannel for unknown URL gracefully" in {
    val cache = new GrpcChannelCache
    noException should be thrownBy cache.shutdownChannel("nonexistent:9090")
  }

  it should "handle shutdownAll with no channels" in {
    val cache = new GrpcChannelCache
    noException should be thrownBy cache.shutdownAll()
  }
}
