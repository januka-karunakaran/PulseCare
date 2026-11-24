# Monetary and Tax Calculations

## 1. Zero Floating-Point Arithmetic
All financial and monetary fields strictly use Java `java.math.BigDecimal` configured with:
- **Scale**: `2`
- **Rounding Mode**: `RoundingMode.HALF_UP`

Under no circumstances is `Double` or `Float` used in business logic, financial balance updates, or ledger calculations.

## 2. Line Item & Invoice Formulas
For every line item $i$:
$$\text{Line Subtotal}_i = \text{quantity}_i \times \text{unitPrice}_i$$
$$\text{Line Discount}_i = \text{discountAmount}_i$$
$$\text{Line Tax}_i = (\text{Line Subtotal}_i - \text{Line Discount}_i) \times \frac{\text{taxRate}_i}{100}$$
$$\text{Line Total}_i = \text{Line Subtotal}_i - \text{Line Discount}_i + \text{Line Tax}_i$$

For the aggregate invoice:
$$\text{Subtotal} = \sum_{i=1}^n \text{Line Subtotal}_i$$
$$\text{Discount} = \sum_{i=1}^n \text{Line Discount}_i + \text{InvoiceDiscount}$$
$$\text{Tax} = \sum_{i=1}^n \text{Line Tax}_i$$
$$\text{Grand Total} = \text{Subtotal} - \text{Discount} + \text{Tax}$$
$$\text{Outstanding Balance} = \text{Grand Total} - \text{Paid Amount}$$
$$\text{Outstanding Balance} \ge 0.00 \quad (\text{Strict Invariant})$$
