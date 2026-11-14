"use client";

import React, { useState } from "react";
import Link from "next/link";
import {
  FaHeartPulse,
  FaCalendarCheck,
  FaArrowRight,
  FaCircleCheck,
  FaStethoscope,
  FaUserDoctor,
  FaHospitalUser,
  FaAward,
  FaBrain,
  FaBone,
  FaBaby,
  FaVenus,
  FaFlask,
  FaLaptopMedical,
  FaHandsHoldingChild,
  FaRightToBracket,
} from "react-icons/fa6";
import { BsGrid3X3GapFill } from "react-icons/bs";

export default function HomePage() {
  const [booking, setBooking] = useState({
    name: "",
    phone: "",
    department: "Cardiology",
    date: "",
  });
  const [bookingSuccess, setBookingSuccess] = useState(false);

  const handleBookingSubmit = (e) => {
    e.preventDefault();
    if (!booking.name || !booking.phone) return;
    setBookingSuccess(true);
    setTimeout(() => {
      setBookingSuccess(false);
      setBooking({ name: "", phone: "", department: "Cardiology", date: "" });
    }, 4000);
  };

  return (
    <div className="careplus-root">
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800;900&display=swap');

        * { box-sizing: border-box; margin: 0; padding: 0; }

        .careplus-root {
          font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
          background: #FFFFFF;
          color: #0F172A;
          overflow-x: hidden;
        }

        /* 1. TOP NAVBAR */
        .cp-navbar-wrap {
          width: 100%;
          background: #FFFFFF;
          border-bottom: 1px solid #F1F5F9;
          position: sticky;
          top: 0;
          z-index: 100;
        }
        .cp-navbar {
          max-width: 1280px;
          margin: 0 auto;
          padding: 18px 32px;
          display: flex;
          align-items: center;
          justify-content: space-between;
        }
        .cp-brand {
          display: flex;
          align-items: center;
          gap: 12px;
          text-decoration: none;
        }
        .cp-brand-cross {
          width: 42px;
          height: 42px;
          position: relative;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .cp-brand-name-box {
          display: flex;
          flex-direction: column;
        }
        .cp-brand-title {
          font-size: 24px;
          font-weight: 900;
          color: #0056D2;
          letter-spacing: -0.5px;
          line-height: 1.1;
        }
        .cp-brand-sub {
          font-size: 9.5px;
          font-weight: 800;
          color: #64748B;
          letter-spacing: 1.2px;
          text-transform: uppercase;
        }

        .cp-nav-links {
          display: flex;
          align-items: center;
          gap: 32px;
        }
        @media (max-width: 980px) { .cp-nav-links { display: none; } }
        .cp-nav-link {
          font-size: 14.5px;
          font-weight: 600;
          color: #334155;
          text-decoration: none;
          position: relative;
          padding: 6px 0;
          transition: color 0.15s;
        }
        .cp-nav-link:hover { color: #0056D2; }
        .cp-nav-link.active {
          color: #0056D2;
          font-weight: 700;
        }
        .cp-nav-link.active::after {
          content: '';
          position: absolute;
          bottom: -2px;
          left: 0;
          width: 100%;
          height: 2.5px;
          background: #0056D2;
          border-radius: 9999px;
        }

        .cp-nav-cta-btn {
          background: #0056D2;
          color: #FFFFFF;
          font-size: 14px;
          font-weight: 700;
          padding: 12px 24px;
          border-radius: 12px;
          text-decoration: none;
          display: inline-flex;
          align-items: center;
          gap: 9px;
          box-shadow: 0 4px 14px rgba(0, 86, 210, 0.25);
          transition: all 0.15s;
        }
        .cp-nav-cta-btn:hover {
          background: #0045A8;
          transform: translateY(-1px);
          box-shadow: 0 6px 18px rgba(0, 86, 210, 0.35);
        }

        /* 2. HERO SECTION (CLEAN PRISTINE CLINICAL THEME - NO BG IMAGE) */
        .cp-hero-section {
          position: relative;
          background: linear-gradient(180deg, #FFFFFF 0%, #F8FAFC 100%);
          padding: 60px 0 90px 0;
          overflow: hidden;
        }
        .cp-hero-inner {
          max-width: 1280px;
          margin: 0 auto;
          padding: 0 32px;
          display: grid;
          grid-template-columns: 1.15fr 1fr;
          align-items: center;
          gap: 40px;
          position: relative;
        }
        @media (max-width: 960px) {
          .cp-hero-inner {
            grid-template-columns: 1fr;
            gap: 48px;
          }
        }

        /* Hero Left Column */
        .cp-hero-left {
          z-index: 10;
        }
        .cp-priority-badge {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          background: #EFF6FF;
          border: 1px solid #BFDBFE;
          padding: 7px 16px;
          border-radius: 9999px;
          font-size: 12.5px;
          font-weight: 700;
          color: #0056D2;
          margin-bottom: 24px;
        }
        .cp-hero-title {
          font-size: clamp(40px, 4.4vw, 58px);
          font-weight: 900;
          color: #0F172A;
          line-height: 1.12;
          letter-spacing: -1.6px;
          margin-bottom: 22px;
        }
        .cp-hero-title span {
          color: #0056D2;
        }
        .cp-hero-sub {
          font-size: 16.5px;
          color: #64748B;
          line-height: 1.65;
          max-width: 500px;
          margin-bottom: 34px;
        }

        .cp-hero-actions {
          display: flex;
          align-items: center;
          gap: 16px;
          margin-bottom: 36px;
          flex-wrap: wrap;
        }
        .cp-btn-primary {
          background: #0056D2;
          color: #FFFFFF;
          font-size: 15px;
          font-weight: 700;
          padding: 14px 28px;
          border-radius: 12px;
          text-decoration: none;
          display: inline-flex;
          align-items: center;
          gap: 10px;
          box-shadow: 0 4px 16px rgba(0, 86, 210, 0.3);
          transition: all 0.15s;
        }
        .cp-btn-primary:hover {
          background: #0045A8;
          transform: translateY(-1px);
          box-shadow: 0 6px 20px rgba(0, 86, 210, 0.4);
        }
        .cp-btn-secondary {
          background: #F8FAFC;
          border: 1px solid #E2E8F0;
          color: #0056D2;
          font-size: 15px;
          font-weight: 700;
          padding: 14px 26px;
          border-radius: 12px;
          text-decoration: none;
          display: inline-flex;
          align-items: center;
          gap: 8px;
          transition: all 0.15s;
        }
        .cp-btn-secondary:hover {
          background: #EFF6FF;
          border-color: #BFDBFE;
        }

        .cp-hero-checks {
          display: flex;
          align-items: center;
          gap: 24px;
          flex-wrap: wrap;
        }
        .cp-check-item {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 13.5px;
          font-weight: 700;
          color: #334155;
        }
        .cp-check-icon {
          color: #0056D2;
          font-size: 16px;
        }

        /* Hero Right Column (Doctor & Floating Stats Card) */
        .cp-hero-right {
          position: relative;
          display: flex;
          align-items: center;
          justify-content: center;
        }
        .cp-doctor-img-wrap {
          position: relative;
          width: 100%;
          max-width: 440px;
          height: 480px;
          display: flex;
          align-items: flex-end;
          justify-content: center;
        }
        .cp-doctor-photo {
          width: 100%;
          height: 100%;
          object-fit: cover;
          object-position: center 35%;
          border-radius: 28px;
          box-shadow: 0 20px 45px -15px rgba(15, 23, 42, 0.18);
        }

        /* Floating Stats Card overlapping right */
        .cp-floating-stat-card {
          position: absolute;
          top: 35%;
          right: -24px;
          background: #FFFFFF;
          border: 1px solid #E2E8F0;
          border-radius: 20px;
          padding: 22px 26px;
          box-shadow: 0 16px 36px -10px rgba(0, 0, 0, 0.12);
          display: flex;
          flex-direction: column;
          gap: 16px;
          z-index: 20;
          min-width: 190px;
        }
        @media (max-width: 600px) {
          .cp-floating-stat-card {
            position: static;
            margin-top: 20px;
            width: 100%;
          }
        }
        .cp-stat-row {
          display: flex;
          align-items: center;
          gap: 14px;
        }
        .cp-stat-icon-circle {
          width: 40px;
          height: 40px;
          background: #EFF6FF;
          border-radius: 12px;
          color: #0056D2;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 18px;
          flex-shrink: 0;
        }
        .cp-stat-content {
          display: flex;
          flex-direction: column;
        }
        .cp-stat-big-num {
          font-size: 22px;
          font-weight: 900;
          color: #0F172A;
          line-height: 1.1;
        }
        .cp-stat-small-label {
          font-size: 11.5px;
          font-weight: 600;
          color: #64748B;
        }
        .cp-stat-divider-h {
          width: 100%;
          height: 1px;
          background: #F1F5F9;
        }

        /* 3. FLOATING 5 SERVICES STRIP */
        .cp-services-strip-wrap {
          max-width: 1280px;
          margin: -45px auto 80px auto;
          padding: 0 32px;
          position: relative;
          z-index: 30;
        }
        .cp-services-strip {
          background: #FFFFFF;
          border: 1px solid #E2E8F0;
          border-radius: 24px;
          padding: 24px 30px;
          display: grid;
          grid-template-columns: repeat(5, 1fr);
          gap: 20px;
          box-shadow: 0 16px 36px -12px rgba(15, 23, 42, 0.08);
        }
        @media (max-width: 1080px) {
          .cp-services-strip { grid-template-columns: repeat(3, 1fr); }
        }
        @media (max-width: 680px) {
          .cp-services-strip { grid-template-columns: 1fr; }
        }
        .cp-strip-item {
          display: flex;
          align-items: flex-start;
          gap: 14px;
          padding: 6px;
        }
        .cp-strip-icon-circle {
          width: 44px;
          height: 44px;
          border-radius: 14px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 20px;
          flex-shrink: 0;
        }
        .cp-strip-item-title {
          font-size: 15px;
          font-weight: 800;
          color: #0F172A;
          margin-bottom: 3px;
        }
        .cp-strip-item-desc {
          font-size: 12px;
          color: #64748B;
          line-height: 1.45;
        }

        /* 4. ABOUT SECTION WITH 3 CARDS */
        .cp-about-section {
          max-width: 1280px;
          margin: 0 auto 90px auto;
          padding: 0 32px;
        }
        .cp-about-grid {
          display: grid;
          grid-template-columns: 1fr 1.6fr;
          gap: 50px;
          align-items: center;
        }
        @media (max-width: 1040px) {
          .cp-about-grid {
            grid-template-columns: 1fr;
          }
        }
        .cp-about-tag {
          font-size: 12px;
          font-weight: 800;
          color: #0056D2;
          text-transform: uppercase;
          letter-spacing: 1.2px;
          margin-bottom: 12px;
        }
        .cp-about-title {
          font-size: clamp(34px, 3.5vw, 46px);
          font-weight: 900;
          color: #0F172A;
          line-height: 1.15;
          letter-spacing: -1.2px;
          margin-bottom: 18px;
        }
        .cp-about-title span {
          color: #0056D2;
        }
        .cp-about-desc {
          font-size: 15.5px;
          color: #64748B;
          line-height: 1.65;
          margin-bottom: 30px;
        }

        .cp-about-stats-row {
          display: flex;
          align-items: center;
          gap: 28px;
          margin-bottom: 34px;
          flex-wrap: wrap;
        }
        .cp-about-stat-item {
          display: flex;
          align-items: center;
          gap: 12px;
        }
        .cp-about-stat-icon {
          width: 38px;
          height: 38px;
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 17px;
          flex-shrink: 0;
        }
        .cp-about-stat-text-box {
          display: flex;
          flex-direction: column;
        }
        .cp-about-stat-num {
          font-size: 18px;
          font-weight: 900;
          color: #0F172A;
          line-height: 1.1;
        }
        .cp-about-stat-label {
          font-size: 11.5px;
          font-weight: 600;
          color: #64748B;
        }

        .cp-btn-about {
          background: #0056D2;
          color: #FFFFFF;
          font-size: 14.5px;
          font-weight: 700;
          padding: 13px 26px;
          border-radius: 12px;
          text-decoration: none;
          display: inline-flex;
          align-items: center;
          gap: 10px;
          box-shadow: 0 4px 14px rgba(0, 86, 210, 0.28);
          transition: all 0.15s;
        }
        .cp-btn-about:hover {
          background: #0045A8;
          transform: translateY(-1px);
        }

        /* 3 Cards on Right */
        .cp-cards-3-grid {
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 20px;
        }
        @media (max-width: 680px) {
          .cp-cards-3-grid { grid-template-columns: 1fr; }
        }
        .cp-visual-card {
          background: #FFFFFF;
          border: 1px solid #E2E8F0;
          border-radius: 20px;
          overflow: hidden;
          box-shadow: 0 4px 16px rgba(0, 0, 0, 0.03);
          transition: all 0.2s;
          display: flex;
          flex-direction: column;
        }
        .cp-visual-card:hover {
          transform: translateY(-4px);
          box-shadow: 0 16px 32px -10px rgba(0, 86, 210, 0.12);
        }
        .cp-card-photo {
          width: 100%;
          height: 170px;
          object-fit: cover;
        }
        .cp-card-body {
          padding: 20px 18px;
          display: flex;
          flex-direction: column;
          gap: 8px;
        }
        .cp-card-badge-row {
          display: flex;
          align-items: center;
          gap: 10px;
        }
        .cp-card-badge-icon {
          width: 32px;
          height: 32px;
          border-radius: 10px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 15px;
          flex-shrink: 0;
        }
        .cp-card-title {
          font-size: 15.5px;
          font-weight: 800;
          color: #0F172A;
          line-height: 1.25;
        }
        .cp-card-desc {
          font-size: 12.5px;
          color: #64748B;
          line-height: 1.5;
        }

        /* 5. QUICK APPOINTMENT SECTION (CLEAN LIGHT CLINICAL THEME) */
        .cp-booking-section {
          max-width: 1280px;
          margin: 0 auto 90px auto;
          padding: 0 32px;
        }
        .cp-booking-box {
          background: #F8FAFC;
          border: 1px solid #E2E8F0;
          border-radius: 32px;
          padding: 52px 56px;
          color: #0F172A;
          display: grid;
          grid-template-columns: 1fr 1.05fr;
          gap: 48px;
          align-items: center;
          box-shadow: 0 12px 36px -12px rgba(15, 23, 42, 0.06);
          position: relative;
        }
        @media (max-width: 900px) {
          .cp-booking-box {
            grid-template-columns: 1fr;
            padding: 36px 24px;
            gap: 32px;
          }
        }
        .cp-booking-badge {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          background: #EFF6FF;
          color: #0056D2;
          border: 1px solid #DBEAFE;
          padding: 6px 14px;
          border-radius: 9999px;
          font-size: 11px;
          font-weight: 800;
          text-transform: uppercase;
          letter-spacing: 0.8px;
          margin-bottom: 16px;
        }
        .cp-booking-title {
          font-size: 36px;
          font-weight: 900;
          letter-spacing: -1px;
          line-height: 1.15;
          margin-bottom: 14px;
          color: #0F172A;
        }
        .cp-booking-title span {
          color: #0056D2;
        }
        .cp-booking-desc {
          font-size: 15px;
          color: #64748B;
          line-height: 1.6;
          margin-bottom: 24px;
        }
        .cp-booking-check-list {
          display: flex;
          flex-direction: column;
          gap: 13px;
        }
        .cp-booking-check-item {
          display: flex;
          align-items: center;
          gap: 10px;
          font-size: 14px;
          font-weight: 600;
          color: #334155;
        }
        .cp-booking-check-icon {
          color: #0056D2;
          font-size: 16px;
          flex-shrink: 0;
        }
        .cp-booking-form-card {
          background: #FFFFFF;
          border: 1px solid #E2E8F0;
          border-radius: 24px;
          padding: 36px 32px;
          color: #0F172A;
          box-shadow: 0 10px 28px -10px rgba(15, 23, 42, 0.08);
        }
        .cp-booking-form-title {
          font-size: 22px;
          font-weight: 900;
          color: #0F172A;
          letter-spacing: -0.3px;
          margin-bottom: 4px;
        }
        .cp-booking-form-sub {
          font-size: 13px;
          color: #64748B;
          margin-bottom: 22px;
        }
        .cp-input-group {
          margin-bottom: 16px;
        }
        .cp-input-label {
          display: block;
          font-size: 11px;
          font-weight: 800;
          color: #334155;
          text-transform: uppercase;
          letter-spacing: 0.5px;
          margin-bottom: 6px;
        }
        .cp-input-field {
          width: 100%;
          border: 1.5px solid #CBD5E1;
          border-radius: 12px;
          padding: 12px 14px;
          font-size: 14px;
          color: #0F172A;
          background: #FFFFFF;
          outline: none;
          transition: all 0.15s ease;
        }
        .cp-input-field:focus {
          border-color: #0056D2;
          box-shadow: 0 0 0 3px rgba(0, 86, 210, 0.12);
        }
        .cp-input-row {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 14px;
        }
        .cp-btn-form-submit {
          width: 100%;
          background: #0056D2;
          color: #FFFFFF;
          font-size: 14.5px;
          font-weight: 800;
          padding: 13px;
          border: none;
          border-radius: 9999px;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 8px;
          margin-top: 10px;
          box-shadow: 0 4px 14px rgba(0, 86, 210, 0.28);
          transition: all 0.15s;
        }
        .cp-btn-form-submit:hover {
          background: #0045A8;
          transform: translateY(-1px);
          box-shadow: 0 6px 18px rgba(0, 86, 210, 0.35);
        }
        }

        /* 6. CLEAN FOOTER */
        .cp-footer {
          background: #0F172A;
          color: #94A3B8;
          padding: 60px 32px 30px 32px;
          border-top: 1px solid #1E293B;
        }
        .cp-footer-inner {
          max-width: 1280px;
          margin: 0 auto;
        }
        .cp-footer-grid {
          display: grid;
          grid-template-columns: 2fr 1fr 1fr 1.5fr;
          gap: 48px;
          padding-bottom: 40px;
          border-bottom: 1px solid #1E293B;
        }
        @media (max-width: 880px) {
          .cp-footer-grid { grid-template-columns: 1fr 1fr; gap: 36px; }
        }
        @media (max-width: 540px) {
          .cp-footer-grid { grid-template-columns: 1fr; gap: 32px; }
        }
        .cp-footer-col-title {
          font-size: 13px;
          font-weight: 800;
          color: #FFFFFF;
          text-transform: uppercase;
          letter-spacing: 0.8px;
          margin-bottom: 16px;
        }
        .cp-footer-links {
          list-style: none;
          display: flex;
          flex-direction: column;
          gap: 10px;
        }
        .cp-footer-links a {
          color: #94A3B8;
          font-size: 13.5px;
          text-decoration: none;
          transition: color 0.15s;
        }
        .cp-footer-links a:hover { color: #FFFFFF; }
        .cp-footer-bottom {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding-top: 24px;
          font-size: 12px;
          color: #64748B;
          flex-wrap: wrap;
          gap: 16px;
        }
      `}</style>

      {/* 1. TOP NAVBAR (MATCHING REFERENCE DESIGN) */}
      <div className="cp-navbar-wrap">
        <header className="cp-navbar">
          {/* Logo with Medical Cross */}
          <Link href="/" className="cp-brand">
            <div className="cp-brand-cross">
              <svg width="38" height="38" viewBox="0 0 40 40" fill="none">
                <rect x="14" y="2" width="12" height="36" rx="6" fill="#0056D2" />
                <rect x="2" y="14" width="36" height="12" rx="6" fill="#0056D2" />
                <path d="M10 20H15L18 14L22 26L25 20H30" stroke="#FFFFFF" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
            <div className="cp-brand-name-box">
              <div className="cp-brand-title">PulseCare</div>
              <div className="cp-brand-sub">MULTISPECIALITY CLINIC</div>
            </div>
          </Link>

          {/* Nav Links with active underline */}
          <nav className="cp-nav-links">
            <a href="#hero" className="cp-nav-link active">Home</a>
            <a href="#about" className="cp-nav-link">About Us</a>
            <a href="#services" className="cp-nav-link">Services</a>
            <a href="#booking" className="cp-nav-link">Doctors</a>
            <a href="#services" className="cp-nav-link">Facilities</a>
            <a href="#booking" className="cp-nav-link">Patient Info</a>
            <a href="#footer" className="cp-nav-link">Contact</a>
          </nav>

          {/* Right Action Button */}
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            <Link href="/login" style={{ fontSize: 14, fontWeight: 700, color: "#0F172A", textDecoration: "none" }}>
              Login
            </Link>
            <a href="#booking" className="cp-nav-cta-btn">
              <FaCalendarCheck />
              <span>Book Appointment</span>
            </a>
          </div>
        </header>
      </div>

      {/* 2. HERO SECTION (MATCHING REFERENCE DESIGN) */}
      <section id="hero" className="cp-hero-section">
        <div className="cp-hero-inner">
          {/* Left Text Column */}
          <div className="cp-hero-left">
            <div className="cp-priority-badge">
              <FaHeartPulse />
              <span>Your Health, Our Priority</span>
            </div>

            <h1 className="cp-hero-title">
              Compassionate Care <br />
              for a <span>Healthier You</span>
            </h1>

            <p className="cp-hero-sub">
              At PulseCare Clinic, we combine advanced medical technology with personalized care to help you and your family live a healthier, happier life.
            </p>

            <div className="cp-hero-actions">
              <a href="#booking" className="cp-btn-primary">
                <span>Book an Appointment</span>
                <FaArrowRight />
              </a>
              <a href="#services" className="cp-btn-secondary">
                <span>Explore Services</span>
                <BsGrid3X3GapFill />
              </a>
            </div>

            <div className="cp-hero-checks">
              <div className="cp-check-item">
                <FaCircleCheck className="cp-check-icon" />
                <span>Experienced Doctors</span>
              </div>
              <div className="cp-check-item">
                <FaCircleCheck className="cp-check-icon" />
                <span>Advanced Technology</span>
              </div>
              <div className="cp-check-item">
                <FaCircleCheck className="cp-check-icon" />
                <span>Patient First Approach</span>
              </div>
            </div>
          </div>

          {/* Right Column: Doctor Photo & Floating Stats Card */}
          <div className="cp-hero-right">
            <div className="cp-doctor-img-wrap">
              {/* Doctor with Stethoscope & Glowing Heart (User Photo) */}
              <img
                src="/images/doctor-heart.jpg"
                alt="PulseCare Specialist Doctor"
                className="cp-doctor-photo"
              />

              {/* Floating Stats Card matching reference */}
              <div className="cp-floating-stat-card">
                <div className="cp-stat-row">
                  <div className="cp-stat-icon-circle">
                    <FaHospitalUser />
                  </div>
                  <div className="cp-stat-content">
                    <div className="cp-stat-big-num">15,000+</div>
                    <div className="cp-stat-small-label">Happy Patients</div>
                  </div>
                </div>

                <div className="cp-stat-divider-h"></div>

                <div className="cp-stat-row">
                  <div className="cp-stat-icon-circle" style={{ background: "#F0FDF4", color: "#16A34A" }}>
                    <FaUserDoctor />
                  </div>
                  <div className="cp-stat-content">
                    <div className="cp-stat-big-num">20+</div>
                    <div className="cp-stat-small-label">Expert Doctors</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 3. FLOATING 5 SERVICES STRIP (MATCHING REFERENCE DESIGN) */}
      <div id="services" className="cp-services-strip-wrap">
        <div className="cp-services-strip">
          {/* 1. Cardiology */}
          <div className="cp-strip-item">
            <div className="cp-strip-icon-circle" style={{ background: "#EFF6FF", color: "#0056D2" }}>
              <FaHeartPulse />
            </div>
            <div>
              <div className="cp-strip-item-title">Cardiology</div>
              <div className="cp-strip-item-desc">Comprehensive heart care for a healthier tomorrow.</div>
            </div>
          </div>

          {/* 2. General Medicine */}
          <div className="cp-strip-item">
            <div className="cp-strip-icon-circle" style={{ background: "#F0FDF4", color: "#16A34A" }}>
              <FaStethoscope />
            </div>
            <div>
              <div className="cp-strip-item-title">General Medicine</div>
              <div className="cp-strip-item-desc">Expert care for everyday health concerns.</div>
            </div>
          </div>

          {/* 3. Pediatrics */}
          <div className="cp-strip-item">
            <div className="cp-strip-icon-circle" style={{ background: "#FAF5FF", color: "#9333EA" }}>
              <FaBaby />
            </div>
            <div>
              <div className="cp-strip-item-title">Pediatrics</div>
              <div className="cp-strip-item-desc">Specialized care for your little ones.</div>
            </div>
          </div>

          {/* 4. Gynecology */}
          <div className="cp-strip-item">
            <div className="cp-strip-icon-circle" style={{ background: "#FDF2F8", color: "#DB2777" }}>
              <FaVenus />
            </div>
            <div>
              <div className="cp-strip-item-title">Gynecology</div>
              <div className="cp-strip-item-desc">Compassionate care for women at every stage.</div>
            </div>
          </div>

          {/* 5. Diagnostics */}
          <div className="cp-strip-item">
            <div className="cp-strip-icon-circle" style={{ background: "#FFFBEB", color: "#D97706" }}>
              <FaFlask />
            </div>
            <div>
              <div className="cp-strip-item-title">Diagnostics</div>
              <div className="cp-strip-item-desc">Accurate tests for precise treatment.</div>
            </div>
          </div>
        </div>
      </div>

      {/* 4. ABOUT SECTION (MATCHING REFERENCE DESIGN) */}
      <section id="about" className="cp-about-section">
        <div className="cp-about-grid">
          {/* Left Column */}
          <div>
            <div className="cp-about-tag">ABOUT PULSECARE CLINIC</div>
            <h2 className="cp-about-title">
              Trusted Care, <br />
              Every <span>Step of the Way</span>
            </h2>

            <p className="cp-about-desc">
              We are committed to providing exceptional healthcare services in a safe, comfortable, and patient-friendly environment.
            </p>

            <div className="cp-about-stats-row">
              <div className="cp-about-stat-item">
                <div className="cp-about-stat-icon" style={{ background: "#EFF6FF", color: "#0056D2" }}>
                  <FaUserDoctor />
                </div>
                <div className="cp-about-stat-text-box">
                  <div className="cp-about-stat-num">20+</div>
                  <div className="cp-about-stat-label">Expert Doctors</div>
                </div>
              </div>

              <div className="cp-about-stat-item">
                <div className="cp-about-stat-icon" style={{ background: "#F0FDF4", color: "#16A34A" }}>
                  <FaHospitalUser />
                </div>
                <div className="cp-about-stat-text-box">
                  <div className="cp-about-stat-num">15,000+</div>
                  <div className="cp-about-stat-label">Happy Patients</div>
                </div>
              </div>

              <div className="cp-about-stat-item">
                <div className="cp-about-stat-icon" style={{ background: "#FAF5FF", color: "#9333EA" }}>
                  <FaAward />
                </div>
                <div className="cp-about-stat-text-box">
                  <div className="cp-about-stat-num">10+</div>
                  <div className="cp-about-stat-label">Years of Excellence</div>
                </div>
              </div>
            </div>

            <a href="#booking" className="cp-btn-about">
              <span>Learn More About Us</span>
              <FaArrowRight />
            </a>
          </div>

          {/* Right Column: 3 Cards Grid */}
          <div className="cp-cards-3-grid">
            {/* Card 1 */}
            <div className="cp-visual-card">
              <img
                src="https://images.unsplash.com/photo-1576091160550-2173dba999ef?auto=format&fit=crop&w=600&q=80"
                alt="Personalized Care"
                className="cp-card-photo"
              />
              <div className="cp-card-body">
                <div className="cp-card-badge-row">
                  <div className="cp-card-badge-icon" style={{ background: "#EFF6FF", color: "#0056D2" }}>
                    <FaStethoscope />
                  </div>
                  <div className="cp-card-title">Personalized Care</div>
                </div>
                <div className="cp-card-desc">Care tailored to your unique health needs.</div>
              </div>
            </div>

            {/* Card 2 */}
            <div className="cp-visual-card">
              <img
                src="https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?auto=format&fit=crop&w=600&q=80"
                alt="Advanced Technology"
                className="cp-card-photo"
              />
              <div className="cp-card-body">
                <div className="cp-card-badge-row">
                  <div className="cp-card-badge-icon" style={{ background: "#F0FDF4", color: "#16A34A" }}>
                    <FaLaptopMedical />
                  </div>
                  <div className="cp-card-title">Advanced Tech</div>
                </div>
                <div className="cp-card-desc">State-of-the-art diagnostic equipment.</div>
              </div>
            </div>

            {/* Card 3 */}
            <div className="cp-visual-card">
              <img
                src="https://images.unsplash.com/photo-1584515979956-d9f6e5d09982?auto=format&fit=crop&w=600&q=80"
                alt="Compassionate Support"
                className="cp-card-photo"
              />
              <div className="cp-card-body">
                <div className="cp-card-badge-row">
                  <div className="cp-card-badge-icon" style={{ background: "#FDF2F8", color: "#DB2777" }}>
                    <FaHandsHoldingChild />
                  </div>
                  <div className="cp-card-title">Compassionate Care</div>
                </div>
                <div className="cp-card-desc">We're here for you with warmth and compassion.</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 5. QUICK APPOINTMENT SECTION */}
      <section id="booking" className="cp-booking-section">
        <div className="cp-booking-box">
          <div>
            <div className="cp-booking-badge">
              <FaCalendarCheck /> Fast Online Scheduling
            </div>
            <h2 className="cp-booking-title">
              Schedule Your <br />
              <span>Consultation</span>
            </h2>
            <p className="cp-booking-desc">
              Choose your medical department, pick a convenient date, and reserve your doctor visit with instant digital confirmation.
            </p>
            <div className="cp-booking-check-list">
              <div className="cp-booking-check-item">
                <FaCircleCheck className="cp-booking-check-icon" />
                <span>20+ Board-Certified Specialist Doctors</span>
              </div>
              <div className="cp-booking-check-item">
                <FaCircleCheck className="cp-booking-check-icon" />
                <span>Free review consultation within 7 days</span>
              </div>
              <div className="cp-booking-check-item">
                <FaCircleCheck className="cp-booking-check-icon" />
                <span>Zero waiting time with digital pre-booking</span>
              </div>
            </div>
          </div>

          <div className="cp-booking-form-card">
            <h3 className="cp-booking-form-title">Book an Appointment</h3>
            <p className="cp-booking-form-sub">Fill in your details to reserve your consultation</p>

            {bookingSuccess ? (
              <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", borderRadius: 16, padding: 24, textAlign: "center", color: "#065F46" }}>
                <FaCircleCheck style={{ fontSize: 32, color: "#10B981", margin: "0 auto 8px auto" }} />
                <div style={{ fontSize: 18, fontWeight: 800 }}>Appointment Requested!</div>
                <div style={{ fontSize: 13, marginTop: 4 }}>Our clinical team will call you to confirm your appointment time.</div>
              </div>
            ) : (
              <form onSubmit={handleBookingSubmit}>
                <div className="cp-input-group">
                  <label className="cp-input-label">Full Name</label>
                  <input
                    type="text"
                    required
                    value={booking.name}
                    onChange={(e) => setBooking({ ...booking, name: e.target.value })}
                    placeholder="e.g. John Doe"
                    className="cp-input-field"
                  />
                </div>

                <div className="cp-input-group">
                  <label className="cp-input-label">Phone Number</label>
                  <input
                    type="tel"
                    required
                    value={booking.phone}
                    onChange={(e) => setBooking({ ...booking, phone: e.target.value })}
                    placeholder="e.g. +91 98765 43210"
                    className="cp-input-field"
                  />
                </div>

                <div className="cp-input-row cp-input-group">
                  <div>
                    <label className="cp-input-label">Department</label>
                    <select
                      value={booking.department}
                      onChange={(e) => setBooking({ ...booking, department: e.target.value })}
                      className="cp-input-field"
                    >
                      <option value="Cardiology">Cardiology</option>
                      <option value="General Medicine">General Medicine</option>
                      <option value="Pediatrics">Pediatrics</option>
                      <option value="Gynecology">Gynecology</option>
                      <option value="Diagnostics">Diagnostics</option>
                    </select>
                  </div>
                  <div>
                    <label className="cp-input-label">Date</label>
                    <input
                      type="date"
                      required
                      value={booking.date}
                      onChange={(e) => setBooking({ ...booking, date: e.target.value })}
                      className="cp-input-field"
                    />
                  </div>
                </div>

                <button type="submit" className="cp-btn-form-submit">
                  <FaCalendarCheck />
                  <span>Confirm & Book Appointment</span>
                </button>
              </form>
            )}
          </div>
        </div>
      </section>

      {/* 6. CLEAN FOOTER */}
      <footer id="footer" className="cp-footer">
        <div className="cp-footer-inner">
          <div className="cp-footer-grid">
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
                <div className="cp-brand-cross" style={{ width: 32, height: 32 }}>
                  <svg width="32" height="32" viewBox="0 0 40 40" fill="none">
                    <rect x="14" y="2" width="12" height="36" rx="6" fill="#0056D2" />
                    <rect x="2" y="14" width="36" height="12" rx="6" fill="#0056D2" />
                  </svg>
                </div>
                <div style={{ fontSize: 20, fontWeight: 900, color: "#FFFFFF" }}>PulseCare</div>
              </div>
              <p style={{ fontSize: 13, lineHeight: 1.6, maxWidth: 300, color: "#94A3B8" }}>
                Committed to delivering compassionate clinical care with modern technology and patient-first expertise.
              </p>
            </div>

            <div>
              <div className="cp-footer-col-title">Quick Links</div>
              <ul className="cp-footer-links">
                <li><a href="#hero">Home</a></li>
                <li><a href="#about">About Us</a></li>
                <li><a href="#services">Services</a></li>
                <li><Link href="/login">Portal Login</Link></li>
                <li><a href="#booking">Book Appointment</a></li>
              </ul>
            </div>

            <div>
              <div className="cp-footer-col-title">Specialities</div>
              <ul className="cp-footer-links">
                <li><a href="#services">Cardiology</a></li>
                <li><a href="#services">General Medicine</a></li>
                <li><a href="#services">Pediatrics</a></li>
                <li><a href="#services">Gynecology</a></li>
                <li><a href="#services">Diagnostics</a></li>
              </ul>
            </div>

            <div>
              <div className="cp-footer-col-title">Emergency Contact</div>
              <div style={{ background: "#1E293B", padding: "16px 20px", borderRadius: 16, border: "1px solid #334155" }}>
                <div style={{ fontSize: 11, color: "#94A3B8" }}>24/7 Clinical Helpline</div>
                <div style={{ fontSize: 18, fontWeight: 900, color: "#FFFFFF", marginTop: 2 }}>+91 123 456 7890</div>
                <div style={{ fontSize: 11, color: "#38BDF8", fontWeight: 700, marginTop: 4 }}>PulseCare Emergency Center</div>
              </div>
            </div>
          </div>

          <div className="cp-footer-bottom">
            <div>© 2026 PulseCare Multispeciality Clinic. All rights reserved.</div>
            <div style={{ display: "flex", gap: 18 }}>
              <a href="#" style={{ color: "#64748B", textDecoration: "none" }}>Privacy Policy</a>
              <a href="#" style={{ color: "#64748B", textDecoration: "none" }}>Terms of Service</a>
              <a href="#" style={{ color: "#64748B", textDecoration: "none" }}>HIPAA Compliant</a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
