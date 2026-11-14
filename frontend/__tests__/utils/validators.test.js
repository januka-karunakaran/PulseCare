import { isValidEmail, isValidPhone, isValidPassword } from "@/utils/validators";

describe("Enterprise Form Validators", () => {
  test("isValidEmail identifies valid and invalid emails", () => {
    expect(isValidEmail("doctor@hospital.com")).toBe(true);
    expect(isValidEmail("patient@domain.org")).toBe(true);
    expect(isValidEmail("invalid-email")).toBe(false);
    expect(isValidEmail("")).toBe(false);
  });

  test("isValidPhone validates phone numbers correctly", () => {
    expect(isValidPhone("+94771234567")).toBe(true);
    expect(isValidPhone("0771234567")).toBe(true);
    expect(isValidPhone("abc")).toBe(false);
  });

  test("isValidPassword enforces minimum length of 6", () => {
    expect(isValidPassword("secure123")).toBe(true);
    expect(isValidPassword("12345")).toBe(false);
  });
});
