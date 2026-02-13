#!/usr/bin/env node

/**
 * Copies the provider.proto file from the Scala module-provider-sdk to the
 * TypeScript SDK's proto/ directory. This ensures a single source of truth
 * for the protobuf definition.
 */

import { cpSync, mkdirSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const sdkRoot = resolve(__dirname, "..");
const repoRoot = resolve(sdkRoot, "..", "..");

const srcProto = resolve(
  repoRoot,
  "modules",
  "module-provider-sdk",
  "src",
  "main",
  "protobuf",
  "constellation",
  "provider",
  "v1",
  "provider.proto",
);

const destDir = resolve(sdkRoot, "proto", "constellation", "provider", "v1");
const destProto = resolve(destDir, "provider.proto");

if (!existsSync(srcProto)) {
  console.error(`Source proto not found: ${srcProto}`);
  console.error("Make sure you are in the constellation-engine repository.");
  process.exit(1);
}

mkdirSync(destDir, { recursive: true });
cpSync(srcProto, destProto);
console.log(`Copied proto: ${srcProto} -> ${destProto}`);
