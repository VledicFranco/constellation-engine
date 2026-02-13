import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: true,
    include: ["tests/**/*.test.ts"],
    coverage: {
      provider: "v8",
      include: ["src/**/*.ts"],
      exclude: [
        "src/generated/**",
        "src/index.ts",
        "src/transport/grpc-provider-transport.ts",
        "src/transport/grpc-executor-server.ts",
      ],
      thresholds: {
        statements: 78,
        branches: 76,
        functions: 78,
        lines: 78,
      },
    },
  },
});
