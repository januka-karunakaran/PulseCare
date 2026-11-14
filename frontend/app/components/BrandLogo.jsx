"use client";

import { FaHeartPulse } from "react-icons/fa6";

export default function BrandLogo({ size = 40, className = "" }) {
  return (
    <div
      className={`rounded-xl bg-gradient-to-br from-[#0056D2] to-[#0284C7] flex items-center justify-center text-white shadow-md shadow-blue-500/25 ${className}`}
      style={{ width: size, height: size, minWidth: size, minHeight: size }}
    >
      <FaHeartPulse style={{ fontSize: size * 0.55 }} />
    </div>
  );
}
