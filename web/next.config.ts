import type { NextConfig } from "next";

const backend = process.env.BACKEND_URL ?? "http://localhost:8082";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      // /api/v1/* is handled by the BFF route (httpOnly cookies → upstream auth).
      // Only health/OpenAPI stay as transparent rewrites (no secrets).
      { source: "/q/:path*", destination: `${backend}/q/:path*` },
    ];
  },
};

export default nextConfig;
