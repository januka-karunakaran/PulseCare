import { ThemeProvider } from "./components/ThemeProvider";
import "./globals.css";

export const metadata = {
  title: "PulseCare | Modern Multi-Speciality Clinic & Health System",
  description: "PulseCare — Next-generation multi-speciality clinical care, advanced medical technology, and 24/7 patient-first healthcare.",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body suppressHydrationWarning>
        <ThemeProvider>
          {children}
        </ThemeProvider>
      </body>
    </html>
  );
}
