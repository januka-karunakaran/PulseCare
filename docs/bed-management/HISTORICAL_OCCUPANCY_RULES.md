# Historical Bed Occupancy Calculation Rules

## 1. Interval Overlap Formula
For a requested report window `[W_start, W_end)` and an occupancy interval `[O_start, O_end)`:
An occupancy overlaps the window iff:
$$O_{start} < W_{end} \land (O_{end} = \text{null} \lor O_{end} > W_{start})$$

The effective overlap duration in hours is:
$$\text{Duration} = \max\left(0, \frac{\min(O_{end}, W_{end}) - \max(O_{start}, W_{start})}{3600}\right)$$

## 2. Staffed Bed Hours & Occupancy Rate
$$\text{Total Available Bed Hours} = \text{Eligible Active Beds} \times \text{Window Duration (Hours)}$$
$$\text{Occupancy Rate (\%)} = \frac{\text{Total Occupied Bed Hours}}{\text{Total Available Bed Hours}} \times 100$$
(Guarded with zero denominator safety: returns 0.0% if available hours equal 0).

## 3. Data Completeness Standards
- `COMPLETE`: Full event log coverage for entire requested date range.
- `PARTIAL_HISTORY`: Requested date range starts before occupancy event logging cutover.
- `CURRENT_SNAPSHOT_ONLY`: No historical event records found; fallback to real-time snapshot.
- `UNAVAILABLE`: Hospital bed registry unconfigured.
