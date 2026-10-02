/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const base = process.env.SYNCDOC_BASE_PATH || "/";
if (!/^\/(?:[A-Za-z0-9_-]+\/)*$/.test(base)) {
  throw new Error("SYNCDOC_BASE_PATH must be an absolute path with a trailing slash");
}
export default defineConfig({
  base,
  plugins: [react()],
  server: {
    proxy: {
      [`${base}api`]: {
        target: process.env.SYNCDOC_API_PROXY ?? "http://localhost:8080", changeOrigin: false,
        rewrite: (path) => base === "/" ? path : path.slice(base.length - 1),
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./tests/setup.ts"],
    include: ["tests/**/*.test.{ts,tsx}"],
  },
});
