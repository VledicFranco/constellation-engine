import { describe, it, expect, vi, beforeEach } from "vitest";
import { DnsDiscovery } from "../../src/discovery/dns-discovery.js";

// Mock node:dns
vi.mock("node:dns", () => ({
  promises: {
    resolve4: vi.fn(),
  },
}));

describe("DnsDiscovery", () => {
  let mockResolve4: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    const dns = await import("node:dns");
    mockResolve4 = dns.promises.resolve4 as ReturnType<typeof vi.fn>;
    mockResolve4.mockReset();
  });

  it("should resolve hostname to addresses with port", async () => {
    mockResolve4.mockResolvedValue(["10.0.0.1", "10.0.0.2"]);

    const discovery = new DnsDiscovery("my-service.local");
    const instances = await discovery.instances();

    expect(instances).toEqual(["10.0.0.1:9090", "10.0.0.2:9090"]);
    expect(mockResolve4).toHaveBeenCalledWith("my-service.local");
  });

  it("should use custom port", async () => {
    mockResolve4.mockResolvedValue(["192.168.1.1"]);

    const discovery = new DnsDiscovery("my-service.local", 8080);
    const instances = await discovery.instances();

    expect(instances).toEqual(["192.168.1.1:8080"]);
  });

  it("should use default port 9090", async () => {
    mockResolve4.mockResolvedValue(["10.0.0.1"]);

    const discovery = new DnsDiscovery("service.consul");
    const instances = await discovery.instances();

    expect(instances).toEqual(["10.0.0.1:9090"]);
  });

  it("should propagate DNS resolution failure", async () => {
    mockResolve4.mockRejectedValue(new Error("ENOTFOUND"));

    const discovery = new DnsDiscovery("nonexistent.local");
    await expect(discovery.instances()).rejects.toThrow("ENOTFOUND");
  });
});
