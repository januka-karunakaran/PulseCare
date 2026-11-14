"use client";

import Link from "next/link";
import { useState } from "react";
import { loginUser } from "@/services/authService";
import { saveSession } from "@/utils/auth";
import {
  FaEnvelope,
  FaLock,
  FaEye,
  FaEyeSlash,
  FaTriangleExclamation,
  FaShieldHalved,
  FaArrowRight,
  FaHeartPulse,
  FaArrowLeft,
} from "react-icons/fa6";

export default function LoginPage() {
  const [form, setForm] = useState({ email: "", password: "" });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const handleChange = (e) =>
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleLogin = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      setError("");
      const data = await loginUser(form);
      saveSession(data);
      if (data.role === "ADMIN") window.location.href = "/dashboard";
      else if (data.role === "DOCTOR") window.location.href = "/doctor/dashboard";
      else if (data.role === "PATIENT") window.location.href = "/patient/dashboard";
      else window.location.href = "/login";
    } catch (err) {
      setError(err.message || "Invalid email or password. Please verify your credentials.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-root">
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap');

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

        /* Compact 3 Stat Pills (100% Equal Width with Doctor Card) */
        .auth-stats-bar {
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 10px;
          width: 100%;
        }
        .auth-stat-pill {
          background: #FFFFFF;
          border: 1px solid #E2E8F0;
          border-radius: 14px;
          padding: 10px 8px;
          text-align: center;
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
        }
        .auth-stat-pill-num {
          font-size: 16px;
          font-weight: 900;
          color: #0056D2;
        }
        .auth-stat-pill-label {
          font-size: 10.5px;
          color: #64748B;
          font-weight: 600;
        }

        /* Right panel (Fitted to 100vh with Zero Scroll) */
        .auth-right {
          flex: 1;
          height: 100vh;
          max-height: 100vh;
          display: flex;
          flex-direction: column;
          justify-content: center;
          align-items: center;
          padding: 24px 36px;
          background: #FFFFFF;
          overflow: hidden;
        }

        .auth-card {
          width: 100%;
          max-width: 420px;
          background: #FFFFFF;
          border: 1px solid #E2E8F0;
          border-radius: 24px;
          padding: 30px 32px;
          box-shadow: 0 16px 36px -10px rgba(15, 23, 42, 0.08);
        }

        .auth-back-link {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          font-size: 12px;
          font-weight: 700;
          color: #64748B;
          text-decoration: none;
          margin-bottom: 14px;
          transition: color 0.15s;
        }
        .auth-back-link:hover { color: #0056D2; }

        .auth-card-title {
          font-size: 24px;
          font-weight: 900;
          color: #0F172A;
          letter-spacing: -0.5px;
          margin-bottom: 4px;
        }
        .auth-card-sub {
          font-size: 13px;
          color: #64748B;
          margin-bottom: 18px;
          line-height: 1.4;
        }

        .auth-error {
          background: #FEF2F2;
          border: 1px solid #FEE2E2;
          color: #991B1B;
          padding: 10px 14px;
          border-radius: 10px;
          font-size: 12px;
          margin-bottom: 16px;
          display: flex;
          align-items: flex-start;
          gap: 8px;
        }

        .auth-field {
          margin-bottom: 14px;
        }
        .auth-label {
          display: block;
          font-size: 11px;
          font-weight: 700;
          color: #334155;
          margin-bottom: 5px;
          letter-spacing: 0.3px;
          text-transform: uppercase;
        }
        .auth-input-wrap {
          position: relative;
        }
        .auth-input {
          width: 100%;
          background: #FFFFFF;
          border: 1.5px solid #CBD5E1;
          border-radius: 12px;
          padding: 10px 14px 10px 38px;
          color: #0F172A;
          font-size: 13.5px;
          outline: none;
          transition: all 0.15s ease;
        }
        .auth-input::placeholder { color: #94A3B8; }
        .auth-input:focus {
          border-color: #0056D2;
          box-shadow: 0 0 0 3px rgba(0, 86, 210, 0.12);
        }
        .auth-input-icon {
          position: absolute;
          left: 13px;
          top: 50%;
          transform: translateY(-50%);
          color: #94A3B8;
          font-size: 14px;
          pointer-events: none;
        }
        .auth-eye-btn {
          position: absolute;
          right: 12px;
          top: 50%;
          transform: translateY(-50%);
          background: none;
          border: none;
          cursor: pointer;
          color: #94A3B8;
          font-size: 14px;
          padding: 2px;
        }
        .auth-eye-btn:hover { color: #0056D2; }

        .auth-row {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 18px;
        }
        .auth-check-label {
          display: flex;
          align-items: center;
          gap: 7px;
          font-size: 12.5px;
          color: #475569;
          font-weight: 500;
          cursor: pointer;
        }
        .auth-check-label input[type=checkbox] {
          width: 15px;
          height: 15px;
          accent-color: #0056D2;
          cursor: pointer;
        }
        .auth-forgot {
          font-size: 12.5px;
          color: #0056D2;
          font-weight: 600;
          text-decoration: none;
        }
        .auth-forgot:hover { text-decoration: underline; }

        .auth-btn {
          width: 100%;
          background: #0056D2;
          border: none;
          border-radius: 9999px;
          padding: 12px;
          color: #FFFFFF;
          font-size: 14px;
          font-weight: 700;
          cursor: pointer;
          box-shadow: 0 4px 14px rgba(0, 86, 210, 0.28);
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          transition: all 0.15s ease;
        }
        .auth-btn:hover:not(:disabled) {
          background: #00419e;
          transform: translateY(-1px);
        }
        .auth-btn:disabled { opacity: 0.6; cursor: not-allowed; }

        .auth-divider {
          display: flex;
          align-items: center;
          gap: 10px;
          margin: 16px 0;
        }
        .auth-divider-line {
          flex: 1;
          height: 1px;
          background: #E2E8F0;
        }
        .auth-divider-text {
          font-size: 11px;
          color: #94A3B8;
          font-weight: 600;
          text-transform: uppercase;
        }

        .auth-bottom-link {
          text-align: center;
          font-size: 13px;
          color: #64748B;
        }
        .auth-bottom-link a {
          color: #0056D2;
          font-weight: 700;
          text-decoration: none;
          margin-left: 4px;
        }
        .auth-bottom-link a:hover { text-decoration: underline; }

        .auth-security-note {
          margin-top: 14px;
          padding-top: 12px;
          border-top: 1px solid #F1F5F9;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 6px;
          font-size: 11px;
          color: #94A3B8;
        }
      `}</style>

      {/* Left Clinical Panel (Single Screen View - Perfectly Centered & Aligned) */}
      <div className="auth-left">
        <div className="auth-left-inner">
          {/* Brand Header */}
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

          {/* Hero Copy */}
          <div className="auth-hero-copy">
            <h1 className="auth-hero-title">
              Compassionate Care. <br />
              <span>Every Patient.</span>
            </h1>
            <p className="auth-hero-sub">
              Reliable clinical care, certified specialist physicians, and 24/7 medical emergency across the region.
            </p>
          </div>

          {/* Doctor, Stethoscope & Heart Visual Card (Clean, Unobstructed) */}
          <div className="doctor-visual-card">
            <div className="doctor-img-overlay"></div>

            {/* User's Exact Photo */}
            <img
              src="/images/doctor-heart.jpg"
              alt="Doctor with Stethoscope and Heart"
              className="doctor-img"
            />

            <div className="doctor-badge-top">
              <span style={{ width: 7, height: 7, borderRadius: "50%", background: "#10B981", display: "inline-block" }}></span>
              <FaHeartPulse style={{ color: "#EF4444" }} /> Clinical Care Active
            </div>
          </div>

          {/* 3 Metric Pills (Perfect 100% Alignment with Doctor Card) */}
          <div className="auth-stats-bar">
            <div className="auth-stat-pill">
              <div className="auth-stat-pill-num">15,000+</div>
              <div className="auth-stat-pill-label">Happy Patients</div>
            </div>
            <div className="auth-stat-pill">
              <div className="auth-stat-pill-num">20+</div>
              <div className="auth-stat-pill-label">Expert Doctors</div>
            </div>
            <div className="auth-stat-pill">
              <div className="auth-stat-pill-num">99%</div>
              <div className="auth-stat-pill-label">Satisfaction Rate</div>
            </div>
          </div>
        </div>
      </div>

      {/* Right Login Panel (Fitted Perfectly to Screen) */}
      <div className="auth-right">
        <div className="auth-card">
          <Link href="/" className="auth-back-link">
            <FaArrowLeft /> Back to Website
          </Link>

          <h2 className="auth-card-title">Welcome Back</h2>
          <p className="auth-card-sub">Sign in to your clinical account to continue</p>

          {error && (
            <div className="auth-error">
              <FaTriangleExclamation style={{ fontSize: "14px", flexShrink: 0, marginTop: "1px" }} />
              <div>{error}</div>
            </div>
          )}

          <form onSubmit={handleLogin}>
            <div className="auth-field">
              <label className="auth-label" htmlFor="login-email">Email Address</label>
              <div className="auth-input-wrap">
                <FaEnvelope className="auth-input-icon" />
                <input
                  id="login-email"
                  type="email"
                  name="email"
                  value={form.email}
                  onChange={handleChange}
                  placeholder="doctor@pulsecare.com or patient@pulsecare.com"
                  className="auth-input"
                  required
                  autoComplete="email"
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-label" htmlFor="login-password">Password</label>
              <div className="auth-input-wrap">
                <FaLock className="auth-input-icon" />
                <input
                  id="login-password"
                  type={showPassword ? "text" : "password"}
                  name="password"
                  value={form.password}
                  onChange={handleChange}
                  placeholder="••••••••"
                  className="auth-input"
                  style={{ paddingRight: 38 }}
                  required
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="auth-eye-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <FaEyeSlash /> : <FaEye />}
                </button>
              </div>
            </div>

            <div className="auth-row">
              <label className="auth-check-label">
                <input type="checkbox" defaultChecked />
                Remember me
              </label>
              <Link href="/forgot-password" className="auth-forgot">Forgot password?</Link>
            </div>

            <button type="submit" className="auth-btn" disabled={loading}>
              {loading ? (
                "Signing in..."
              ) : (
                <>
                  Sign In to Portal <FaArrowRight />
                </>
              )}
            </button>
          </form>

          <div className="auth-divider">
            <div className="auth-divider-line"></div>
            <div className="auth-divider-text">Need an account?</div>
            <div className="auth-divider-line"></div>
          </div>

          <div className="auth-bottom-link">
            New patient? <Link href="/register">Register New Account &rarr;</Link>
          </div>

          <div className="auth-security-note">
            <FaShieldHalved style={{ color: "#10B981" }} />
            <span>256-bit SSL Encrypted • HIPAA Compliant</span>
          </div>
        </div>
      </div>
    </div>
  );
}
