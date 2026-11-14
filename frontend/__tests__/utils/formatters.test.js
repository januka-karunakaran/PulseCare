import { formatCurrency, capitalize, truncate } from "@/utils/formatters";

describe("Enterprise Data Formatters", () => {
  test("formatCurrency formats amounts with 2 decimal places", () => {
    const formatted = formatCurrency(1500, "LKR");
    expect(formatted).toContain("1,500.00");
  });

  test("capitalize capitalizes first letter", () => {
    expect(capitalize("doctor")).toBe("Doctor");
    expect(capitalize("ADMIN")).toBe("Admin");
  });

  test("truncate shortens long strings and appends ellipsis", () => {
    expect(truncate("Short", 10)).toBe("Short");
    expect(truncate("This is a very long text exceeding limit", 10)).toBe("This is a ...");
  });
});
