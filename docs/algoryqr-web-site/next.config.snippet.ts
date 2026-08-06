import type { NextConfig } from "next";

const imageCdnHost = process.env.NEXT_PUBLIC_IMAGE_CDN_BASE
  ? new URL(process.env.NEXT_PUBLIC_IMAGE_CDN_BASE).hostname
  : "localhost";

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: imageCdnHost,
        pathname: "/buckets/qr-product-images/**",
      },
      {
        protocol: "http",
        hostname: "localhost",
        port: "8888",
        pathname: "/buckets/qr-product-images/**",
      },
    ],
  },
};

export default nextConfig;
