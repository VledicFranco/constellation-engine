#!/usr/bin/env node
/**
 * Constellation Engine MCP Server
 *
 * Provides Build/Test and Session Management tools for multi-agent development.
 *
 * Usage:
 *   node dist/index.js
 *
 * The server communicates via stdio using the Model Context Protocol.
 */

import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { createServer } from './server.js';

async function main(): Promise<void> {
  const server = createServer();
  const transport = new StdioServerTransport();

  await server.connect(transport);

  // Handle graceful shutdown
  process.on('SIGINT', async () => {
    await server.close();
    process.exit(0);
  });

  process.on('SIGTERM', async () => {
    await server.close();
    process.exit(0);
  });

  // Keep process running
  process.stdin.resume();
}

main().catch((error) => {
  console.error('Failed to start MCP server:', error);
  process.exit(1);
});
