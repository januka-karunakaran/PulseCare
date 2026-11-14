"use client";

import Link from "next/link";
import { useState } from "react";
import { registerUser, sendSignupOtp } from "@/services/authService";
import {
  FaEnvelope,
  FaTriangleExclamation,
  FaEye,
  FaEyeSlash,
  FaLock,
  FaShieldHalved,
  FaUser,
  FaUserDoctor,
  FaCircleCheck,
  FaHeartPulse,
  FaStethoscope,
  FaArrowRight,
  FaArrowLeft,
} from "react-icons/fa6";

const ROLES = [
  { value: "PATIENT", label: "Patient", icon: <FaUser /> },
  { value: "DOCTOR", label: "Doctor", icon: <FaUserDoctor /> },
  { value: "ADMIN", label: "Admin", icon: <FaShieldHalved /> },
];

export default function RegisterPage() {
  const [form, setForm] = useState({ fullName: "", email: "", password: "", role: "PATIENT" });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [otpSent, setOtpSent] = useState(false);
  const [otp, setOtp] = useState("");

  const handleChange = (e) =>
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleRegister = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      setError("");
      setMessage("");
      if (!otpSent) {
        await sendSignupOtp(form.email.trim());
        setOtpSent(true);
        setMessage(`A 6-digit verification code was sent to ${form.email.trim()}`);
        return;
      }
      await registerUser({ ...form, email: form.email.trim(), otp });
      setMessage("Account created! Redirecting to login...");
      setForm({ fullName: "", email: "", password: "", role: "PATIENT" });
      setOtp("");
      setOtpSent(false);
      setTimeout(() => {
        window.location.href = "/login";
      }, 1400);
    } catch (err) {
      setError(err.message || "Registration failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-root">
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800;900&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }

        html, body {
          height: 100%;
          overflow: hidden;
        }

        .auth-root {
          font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
          height: 100vh;
          max-height: 100vh;
          overflow: hidden;
          display: flex;
          background: #FFFFFF;
        }

        /* Left visual clinical panel (Single-Screen / Perfectly Centered & Aligned) */
        .auth-left {
          display: none;
          flex-direction: column;
          justify-content: center;
          align-items: center;
          padding: 24px 36px;
          width: 50%;
          height: 100vh;
          max-height: 100vh;
          background: #F8FAFC;
          border-right: 1px solid #E2E8F0;
          overflow: hidden;
        }
        @media (min-width: 980px) { .auth-left { display: flex; } }

        .auth-left-inner {
          width: 100%;
          max-width: 440px;
          display: flex;
          flex-direction: column;
        }

        .auth-brand {
          display: flex;
          align-items: center;
          gap: 12px;
          margin-bottom: 18px;
        }
        .auth-brand-icon {
          width: 38px;
          height: 38px;
          background: linear-gradient(135deg, #0056D2, #0284C7);
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 18px;
          color: white;
          box-shadow: 0 4px 12px rgba(0, 86, 210, 0.25);
        }
        .auth-brand-name {
          font-size: 20px;
          font-weight: 900;
          color: #0F172A;
          letter-spacing: -0.4px;
          line-height: 1.1;
        }
        .auth-brand-tagline {
          font-size: 10px;
          font-weight: 700;
          color: #0056D2;
          text-transform: uppercase;
          letter-spacing: 0.8px;
        }

        .auth-hero-copy {
          margin-bottom: 16px;
        }
        .auth-hero-title {
          font-size: clamp(26px, 2.6vw, 34px);
          font-weight: 900;
          color: #0F172A;
          line-height: 1.15;
          letter-spacing: -0.8px;
          margin-bottom: 6px;
        }
        .auth-hero-title span {
          color: #0056D2;
        }
        .auth-hero-sub {
          font-size: 13.5px;
          color: #64748B;
          line-height: 1.5;
        }

        /* Doctor & Stethoscope Card (Matches Width of Stats Exactly) */
        .doctor-visual-card {
          position: relative;
          width: 100%;
          height: 240px;
          border-radius: 20px;
          overflow: hidden;
          background: #0B132B;
          border: 1px solid #E2E8F0;
          box-shadow: 0 12px 28px -10px rgba(15, 23, 42, 0.1);
          margin-bottom: 14px;
        }
        .doctor-img-overlay {
          position: absolute;
          inset: 0;
          background: linear-gradient(180deg, transparent 65%, rgba(15, 23, 42, 0.45) 100%);
          z-index: 2;
          pointer-events: none;
        }
        .doctor-img {
          width: 100%;
          height: 100%;
          object-fit: cover;
          object-position: center 35%;
          display: block;
        }
        .doctor-badge-top {
          position: absolute;
          top: 12px;
          left: 12px;
          z-index: 4;
          background: rgba(255, 255, 255, 0.95);
          backdrop-filter: blur(8px);
          padding: 4px 10px;
          border-radius: 9999px;
          font-size: 11px;
          font-weight: 700;
          color: #0F172A;
          display: flex;
          align-items: center;
          gap: 6px;
          border: 1px solid rgba(226, 232, 240, 0.8);
        }

        /* Compact 3 Features Bar (Equal Width with Doctor Card) */
        .auth-features-bar {
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 10px;
          width: 100%;
        }
        .auth-feature-pill {
          background: #FFFFFF;
          border: 1px solid #E2E8F0;
          border-radius: 12px;
          padding: 9px 8px;
          text-align: center;
          font-size: 11px;
          font-weight: 700;
          color: #334155;
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 5px;
        }
        .auth-feature-pill span.check {
          color: #0056D2;
          font-weight: 900;
        }

        .auth-right {
          flex: 1;
          height: 100vh;
          max-height: 100vh;
          display: flex;
          flex-direction: column;
          justify-content: center;
          align-items: center;
          padding: 20px 36px;
          background: #FFFFFF;
          overflow-y: auto;
        }
        .auth-card {
          width: 100%;
          max-width: 440px;
          background: #FFFFFF;
          border: 1px solid #E2E8F0;
          border-radius: 24px;
          padding: 26px 30px;
          box-shadow: 0 16px 36px -10px rgba(15, 23, 42, 0.08);
        }

        .role-selector {
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 8px;
          margin-bottom: 20px;
        }
        .role-btn {
          border: 1.5px solid #CBD5E1;
          background: #FFFFFF;
          border-radius: 12px;
          padding: 10px 8px;
          cursor: pointer;
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 6px;
          font-size: 12.5px;
          font-weight: 600;
          color: #475569;
          transition: all 0.15s;
        }
        .role-btn:hover {
          border-color: #0056D2;
          color: #0056D2;
        }
        .role-btn.active {
          border-color: #0056D2;
          background: #EFF6FF;
          color: #0056D2;
          font-weight: 700;
        }

        .auth-field {
          margin-bottom: 16px;
        }
        .auth-label {
          display: block;
          font-size: 11.5px;
          font-weight: 700;
          color: #334155;
          margin-bottom: 6px;
          text-transform: uppercase;
          letter-spacing: 0.3px;
        }
        .auth-input-wrap {
          position: relative;
        }
        .auth-input {
          width: 100%;
          background: #FFFFFF;
          border: 1.5px solid #CBD5E1;
          border-radius: 12px;
          padding: 12px 16px 12px 42px;
          color: #0F172A;
          font-size: 14.5px;
          outline: none;
          transition: all 0.15s;
        }
        .auth-input:focus {
          border-color: #0056D2;
          box-shadow: 0 0 0 3px rgba(0, 86, 210, 0.12);
        }
        .auth-input-icon {
          position: absolute;
          left: 14px;
          top: 50%;
          transform: translateY(-50%);
          color: #94A3B8;
          font-size: 16px;
        }
        .auth-eye-btn {
          position: absolute;
          right: 14px;
          top: 50%;
          transform: translateY(-50%);
          background: none;
          border: none;
          cursor: pointer;
          color: #94A3B8;
          font-size: 16px;
        }

        .auth-btn {
          width: 100%;
          background: #0056D2;
          border: none;
          border-radius: 9999px;
          padding: 14px;
          color: #FFFFFF;
          font-size: 15px;
          font-weight: 700;
          cursor: pointer;
          transition: all 0.15s;
          box-shadow: 0 4px 14px rgba(0, 86, 210, 0.3);
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          margin-top: 10px;
        }
        .auth-btn:hover:not(:disabled) {
          background: #00419e;
          transform: translateY(-1px);
        }
        .auth-btn:disabled { opacity: 0.6; cursor: not-allowed; }

        .auth-message {
          background: #ECFDF5;
          border: 1px solid #A7F3D0;
          color: #065F46;
          padding: 12px;
          border-radius: 12px;
          font-size: 13px;
          margin-bottom: 18px;
        }
        .auth-error {
          background: #FEF2F2;
          border: 1px solid #FEE2E2;
          color: #991B1B;
          padding: 12px;
          border-radius: 12px;
          font-size: 13px;
          margin-bottom: 18px;
        }
      `}</style>

      {/* Left Panel (Single Screen View - Perfectly Centered & Aligned) */}
      <div className="auth-left">
        <div className="auth-left-inner">
          <div className="auth-brand">
            <div className="auth-brand-icon">
              <FaHeartPulse />
            </div>
            <div>
              <div className="auth-brand-name">
                PulseCare <span style={{ color: "#0056D2" }}>Clinic</span>
              </div>
              <div className="auth-brand-tagline">Multispeciality Clinic</div>
            </div>
          </div>

          <div className="auth-hero-copy">
            <h1 className="auth-hero-title">
              Join Our Care. <br />
              <span>Protect Your Health.</span>
            </h1>
            <p className="auth-hero-sub">
              Create your account to book doctor appointments, access prescriptions, and manage medical records digitally.
            </p>
          </div>

          {/* Doctor with Stethoscope & Heart */}
          <div className="doctor-visual-card">
            <div className="doctor-img-overlay"></div>
            <img
              src="/images/doctor-heart.jpg"
              alt="PulseCare Doctor with Stethoscope and Heart"
              className="doctor-img"
            />
            <div className="doctor-badge-top">
              <span style={{ width: 7, height: 7, borderRadius: "50%", background: "#10B981", display: "inline-block" }}></span>
              <FaHeartPulse style={{ color: "#EF4444" }} /> Clinical Care Active
            </div>
          </div>

          {/* 3 Symmetrical Features Bar (100% Equal Width with Doctor Card) */}
          <div className="auth-features-bar">
            <div className="auth-feature-pill">
              <span className="check">✓</span> Instant Booking
            </div>
            <div className="auth-feature-pill">
              <span className="check">✓</span> Digital Rx
            </div>
            <div className="auth-feature-pill">
              <span className="check">✓</span> 24/7 Helpline
            </div>
          </div>
        </div>
      </div>

      {/* Right Registration Panel */}
      <div className="auth-right">
        <div className="auth-card">
          <Link href="/login" style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 12, fontWeight: 700, color: "#64748B", textDecoration: "none", marginBottom: 16 }}>
            <FaArrowLeft /> Back to Login
          </Link>

          <h2 style={{ fontSize: 26, fontWeight: 900, color: "#0F172A", marginBottom: 4 }}>Create an Account</h2>
          <p style={{ fontSize: 14, color: "#64748B", marginBottom: 20 }}>Join PulseCare Clinic patient &amp; clinical care network</p>

          {message && <div className="auth-message">{message}</div>}
          {error && <div className="auth-error">{error}</div>}

          {/* Role selector */}
          <div className="role-selector">
            {ROLES.map((r) => (
              <button
                type="button"
                key={r.value}
                className={`role-btn ${form.role === r.value ? "active" : ""}`}
                onClick={() => setForm((prev) => ({ ...prev, role: r.value }))}
              >
                <span style={{ fontSize: 16 }}>{r.icon}</span>
                <span>{r.label}</span>
              </button>
            ))}
          </div>

          <form onSubmit={handleRegister}>
            <div className="auth-field">
              <label className="auth-label">Full Name</label>
              <div className="auth-input-wrap">
                <FaUser className="auth-input-icon" />
                <input
                  type="text"
                  name="fullName"
                  value={form.fullName}
                  onChange={handleChange}
                  placeholder="e.g. John Doe"
                  className="auth-input"
                  required
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-label">Email Address</label>
              <div className="auth-input-wrap">
                <FaEnvelope className="auth-input-icon" />
                <input
                  type="email"
                  name="email"
                  value={form.email}
                  onChange={handleChange}
                  placeholder="john@example.com"
                  className="auth-input"
                  required
                  disabled={otpSent}
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-label">Password</label>
              <div className="auth-input-wrap">
                <FaLock className="auth-input-icon" />
                <input
                  type={showPassword ? "text" : "password"}
                  name="password"
                  value={form.password}
                  onChange={handleChange}
                  placeholder="Create a strong password"
                  className="auth-input"
                  style={{ paddingRight: 44 }}
                  required
                />
                <button
                  type="button"
                  className="auth-eye-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                >
                  {showPassword ? <FaEyeSlash /> : <FaEye />}
                </button>
              </div>
            </div>

            {otpSent && (
              <div className="auth-field">
                <label className="auth-label">6-Digit Verification Code</label>
                <div className="auth-input-wrap">
                  <FaShieldHalved className="auth-input-icon" style={{ color: "#0056D2" }} />
                  <input
                    type="text"
                    name="otp"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value)}
                    placeholder="Enter code sent to email"
                    className="auth-input"
                    maxLength={6}
                    required
                  />
                </div>
              </div>
            )}

            <button type="submit" className="auth-btn" disabled={loading}>
              {loading
                ? "Processing..."
                : otpSent
                ? "Complete Registration"
                : "Send Verification Code"}
              <FaArrowRight />
            </button>
          </form>

          <div style={{ textAlign: "center", marginTop: 20, fontSize: 13.5, color: "#64748B" }}>
            Already have an account?{" "}
            <Link href="/login" style={{ color: "#0056D2", fontWeight: 700, textDecoration: "none" }}>
              Sign In &rarr;
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
