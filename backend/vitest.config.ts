import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // Tests use their own home + people so they never touch the demo graphs.
    env: { HOMEOS_HOME: "test", HOMEOS_PEOPLE: "alice,bob" },
    testTimeout: 60_000,
    hookTimeout: 60_000,
    fileParallelism: false,
  },
});
