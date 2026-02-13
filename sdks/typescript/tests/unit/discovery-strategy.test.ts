import { describe, it, expect } from "vitest";
import { StaticDiscovery } from "../../src/discovery/static-discovery.js";

describe("StaticDiscovery", () => {
  it("should return the configured addresses", async () => {
    const discovery = new StaticDiscovery(["host1:9090", "host2:9090"]);
    const instances = await discovery.instances();
    expect(instances).toEqual(["host1:9090", "host2:9090"]);
  });

  it("should return an empty list for empty config", async () => {
    const discovery = new StaticDiscovery([]);
    const instances = await discovery.instances();
    expect(instances).toEqual([]);
  });

  it("should return a copy, not a reference", async () => {
    const addresses = ["host1:9090"];
    const discovery = new StaticDiscovery(addresses);
    const instances = await discovery.instances();
    instances.push("modified");
    const instances2 = await discovery.instances();
    expect(instances2).toEqual(["host1:9090"]);
  });
});
