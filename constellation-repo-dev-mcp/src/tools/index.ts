/**
 * Tool registry for Constellation Engine MCP Server
 */

import { buildTools } from './build-tools.js';
import { sessionTools } from './session-tools.js';

// Export all tools combined
export const allTools = [...buildTools, ...sessionTools];

// Export individual tool groups
export { buildTools } from './build-tools.js';
export { sessionTools } from './session-tools.js';

// Tool name type for type-safe tool lookup
export type ToolName =
  // Build tools
  | 'constellation_run_tests'
  | 'constellation_get_test_status'
  | 'constellation_get_build_status'
  | 'constellation_run_affected_tests'
  // Session tools
  | 'constellation_get_agent_context'
  | 'constellation_verify_worktree'
  | 'constellation_resume_session'
  | 'constellation_read_queue'
  | 'constellation_handoff_session';

/**
 * Get a tool by name
 */
export function getTool(name: ToolName) {
  return allTools.find((tool) => tool.name === name);
}
