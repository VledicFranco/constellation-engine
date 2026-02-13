/**
 * Canary rollout result types, mirroring Scala's CanaryResult enum.
 */

export type CanaryResult =
  | { readonly status: "Promoted" }
  | { readonly status: "RolledBack"; readonly reason: string }
  | {
      readonly status: "PartialFailure";
      readonly promoted: string[];
      readonly failed: string[];
    };
