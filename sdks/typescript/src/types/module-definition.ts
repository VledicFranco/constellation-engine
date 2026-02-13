/**
 * Describes a module that a provider offers for execution.
 *
 * Mirrors the Scala `ModuleDefinition` case class.
 */

import type { CType } from "./ctype.js";
import type { CValue } from "./cvalue.js";
import { TypeSchemaConverter } from "../serialization/type-schema-converter.js";
import type { ModuleDeclaration } from "../transport/transport.js";

export interface ModuleDefinition {
  /** Short module name (e.g., "analyze"). */
  name: string;
  /** CType for the module's input. */
  inputType: CType;
  /** CType for the module's output. */
  outputType: CType;
  /** Semantic version string (informational). */
  version: string;
  /** Human-readable description. */
  description: string;
  /** The async function that executes this module. */
  handler: (input: CValue) => Promise<CValue>;
}

/** Convert a ModuleDefinition to a protobuf ModuleDeclaration for registration. */
export function toDeclaration(mod: ModuleDefinition): ModuleDeclaration {
  return {
    name: mod.name,
    inputSchema: TypeSchemaConverter.toTypeSchema(mod.inputType),
    outputSchema: TypeSchemaConverter.toTypeSchema(mod.outputType),
    version: mod.version,
    description: mod.description,
  };
}

/** Produce the fully qualified module name within a namespace. */
export function qualifiedName(mod: ModuleDefinition, namespace: string): string {
  return `${namespace}.${mod.name}`;
}
