"""
make_demo_predictions.py - builds the input CSV for publish_predictions.py.

Until the live prediction engine is validated, we "replay" the past: pick one
dekad (10-day period) from history, score all 47 counties with the saved
model, and write one CSV row per county.

IMPORTANT: it uses backend/assets/model.json, which was trained on
2002-2019 ONLY (and calibrated on 2002-2019 out-of-fold predictions).
Nothing is refitted on the chosen dekad, so replaying April 2024 is a fair
demo, not a model that has "seen" the answer.

Output columns (what publish_predictions.py expects):
  county_code     admin1Pcode, e.g. KE047
  risk_band       low / elevated / high
  prob            the model's raw number. Kept for debugging ONLY: the app
                  must never show it to users.
  chance          calibrated chance of flooding (0..1): the real flood rate
                  seen for counties with this raw number. NOT rounded: the
                  app rounds it for display (nearest 5%)
  rain_needed_mm  the 10-day rain (mm) that would lift the county to the
                  "high" threshold; empty if it is already high
  reasons         JSON list of exactly 3 plain sentences: why the risk is
                  what it is (for "low" counties: why it is NOT high)
  as_of           the dekad's start date, YYYY-MM-DD

Usage:
  python make_demo_predictions.py --date 2024-04-21
  python make_demo_predictions.py --date 2022-03-21
"""

import argparse
import csv
import json
from pathlib import Path

import numpy as np
import pandas as pd

HERE = Path(__file__).parent
MODEL_JSON = HERE / "assets" / "model.json"
# The ML repo's county table (it is NOT in this repo, so pass --features
# if it lives somewhere else on your machine).
DEFAULT_FEATURES = (
    r"C:\UNI\YR 3\3.2\Artificial Intelligence\Project"
    r"\data\processed\county_features.csv"
)

# Band cut-offs. HIGH is the threshold picked in the ML protocol (model.json).
# ELEVATED = 0.5, the model's natural decision boundary (balanced classes).
ELEVATED_CUT = 0.5

# For each feature group: a sentence when the group RAISES the risk ("up")
# and one when it LOWERS it ("down"). {..} are filled with the county's real
# values for that dekad.
SENTENCES = {
    "this_10_days": {
        "neutral": "Rain over the last 10 days was {rfq:.0f}% of normal ({rfh:.0f} mm).",
        "up": "Rain over the last 10 days was {rfq:.0f}% of normal ({rfh:.0f} mm), which raises the flood risk.",
        "down": "Rain over the last 10 days was {rfq:.0f}% of normal ({rfh:.0f} mm), which keeps the flood risk down.",
    },
    "past_month": {
        "neutral": "Rain over the past month was {r1q:.0f}% of normal ({r1h:.0f} mm).",
        "up": "Rain over the past month was {r1q:.0f}% of normal ({r1h:.0f} mm), which raises the flood risk.",
        "down": "Rain over the past month was {r1q:.0f}% of normal ({r1h:.0f} mm), which keeps the flood risk down.",
    },
    "past_3_months": {
        "neutral": "Rain over the past 3 months was {r3q:.0f}% of normal ({r3h:.0f} mm).",
        "up": "Rain over the past 3 months was {r3q:.0f}% of normal ({r3h:.0f} mm), which raises the flood risk.",
        "down": "Rain over the past 3 months was {r3q:.0f}% of normal ({r3h:.0f} mm), which keeps the flood risk down.",
    },
    "time_of_year": {
        "up": "This time of year is when floods are more likely in this county.",
        "down": "This time of year is not when floods are most likely in this county.",
    },
    "rest_of_kenya": {
        "up": "Heavy rain is widespread across Kenya ({nat:.0f}% of areas well above normal), which raises the risk.",
        "down": "Heavy rain is not widespread across Kenya (only {nat:.0f}% of areas well above normal), which keeps the risk down.",
    },
}


def dekad_of_year(date):
    """1..36. Days 1-10 are the 1st dekad of the month, 11-20 the 2nd, 21-end
    the 3rd. Same rule as the ML repo's pipeline/dekad.py (tests/test_dekad.py)."""
    return (date.month - 1) * 3 + min((date.day - 1) // 10, 2) + 1


def prob_of(x, model):
    """Raw probability from a vector of the 17 features (model.json arithmetic)."""
    z = (np.asarray(x) - model["scaler_mean"]) / model["scaler_scale"]
    logit = model["intercept"] + float(z @ np.array(model["coefficients"]))
    return 1 / (1 + np.exp(-logit))


def rain_needed(row, model, dekad):
    """The 10-day rain (mm) at which this county reaches the 'high' threshold.

    We ADD `extra` mm to this dekad's rain and hold everything else fixed,
    but everything that is built from this dekad's rain moves with it:
      - rfh, r1h, r3h go up by `extra` (they are sums that include this dekad)
      - rfq, r1q, r3q go up by extra as a % of the matching normal
      - the _max versions (wettest sub-county) get the same increase
    Lags, the season, the normal and the 'widespread' value stay fixed.
    Then we search for the `extra` that makes the probability hit the
    threshold. Returns None if the county is already high, or if no amount
    of extra rain gets there.
    """
    names = model["features"]
    n = model["normals"][row.county]
    # "Percent of normal" is the training definition 100 * (x + 5) / (normal + 5)
    # (5 mm added to both), so +1 mm of rain adds 100 / (normal + 5) points.
    pct = {
        "rfq": 100 / (n["rfh_avg"][dekad - 1] + 5),
        "r1q": 100 / (n["r1h_avg"][dekad - 1] + 5),
        "r3q": 100 / (n["r3h_avg"][dekad - 1] + 5),
    }

    def prob_with(extra):
        f = row[names].astype(float).copy()
        for k in ("rfh", "rfh_max", "r1h", "r3h"):  # mm
            f[k] += extra
        for k in ("rfq", "rfq_max"):
            f[k] += extra * pct["rfq"]
        for k in ("r1q", "r1q_max"):
            f[k] += extra * pct["r1q"]
        for k in ("r3q", "r3q_max"):
            f[k] += extra * pct["r3q"]
        return prob_of(f.values, model)

    target = model["threshold"]
    if prob_with(0) >= target:
        return None  # already high
    hi = 1000.0  # no more than 1000 extra mm in 10 days
    if prob_with(hi) < target:
        return None  # more rain would not get it there
    lo = 0.0
    for _ in range(60):  # bisection: halve the search range each time
        mid = (lo + hi) / 2
        if prob_with(mid) >= target:
            hi = mid
        else:
            lo = mid
    needed = float(row.rfh) + hi  # total 10-day rain, not just the extra
    assert abs(prob_with(hi) - target) < 1e-6
    return round(needed, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--date", required=True, help="dekad start date, e.g. 2024-04-21")
    ap.add_argument("--features", default=DEFAULT_FEATURES)
    ap.add_argument("--out", default=None, help="default: demo/predictions_<date>.csv")
    a = ap.parse_args()

    model = json.loads(MODEL_JSON.read_text())
    df = pd.read_csv(a.features, parse_dates=["date"])
    day = df[df.date == a.date].copy()
    if len(day) != 47:
        raise SystemExit(f"Expected 47 counties on {a.date}, found {len(day)}. "
                         "Use a dekad start date (the 1st, 11th or 21st).")
    dekad = dekad_of_year(pd.Timestamp(a.date))

    # ---- the model's arithmetic, exactly as in model.json ----
    # z = (x - mean) / scale ; each feature's push = coef * z
    names = model["features"]
    z = (day[names].values - model["scaler_mean"]) / model["scaler_scale"]
    push = z * np.array(model["coefficients"])  # one column per feature
    logit = model["intercept"] + push.sum(axis=1)
    day["prob"] = 1 / (1 + np.exp(-logit))  # logit -> probability 0..1

    # Calibrated chance: look the raw number up in the saved table
    # (linear interpolation, clipped at both ends). We store it UNROUNDED;
    # the app applies the display rule (nearest 5%, under 2.5% shown as
    # "under 5%"); this rule is written in the app handoff notes given to Eric.
    cal = model["calibration"]
    day["chance"] = np.interp(day.prob, cal["x"], cal["y"])

    # Group pushes: sum the features of each group (positive = raises risk).
    push = pd.DataFrame(push, columns=names, index=day.index)
    groups = pd.DataFrame(
        {g: push[fs].sum(axis=1) for g, fs in model["groups"].items()}
    )

    high_cut = model["threshold"]
    rows = []
    for idx, r in day.sort_values("county").iterrows():
        prob = r.prob
        band = "high" if prob >= high_cut else "elevated" if prob >= ELEVATED_CUT else "low"

        # Exactly 3 reasons for EVERY county.
        #  - elevated/high: the 3 groups pushing risk UP the most
        #  - low: the 3 groups holding risk DOWN the most (why it is NOT high)
        # Each sentence says which way that group pushes. A rain group is
        # skipped if its direction would contradict its own figure (e.g.
        # "82% of normal ... raises the risk"); the model can do that because
        # its coefficients partly cancel. Skipped groups only come back, in
        # a neutral sentence, if fewer than 3 groups are left.
        fill = dict(rfq=r.rfq, rfh=r.rfh, r1q=r.r1q, r1h=r.r1h,
                    r3q=r.r3q, r3h=r.r3h, nat=r.nat_frac_anom * 100)
        order = groups.loc[idx].sort_values(ascending=(band == "low"))
        reasons, skipped = [], []
        for g, v in order.items():
            up = v > 0
            pct = {"this_10_days": r.rfq, "past_month": r.r1q,
                   "past_3_months": r.r3q}.get(g)  # None for the other groups
            if pct is not None and (up != (pct >= 100)):
                skipped.append(g)  # direction contradicts the % of normal
            else:
                reasons.append(SENTENCES[g]["up" if up else "down"].format(**fill))
        reasons = reasons[:3]
        for g in skipped:  # top up to 3 with neutral wording
            if len(reasons) < 3:
                reasons.append(SENTENCES[g]["neutral"].format(**fill))

        needed = rain_needed(r, model, dekad)
        rows.append({
            "county_code": r.county,
            "risk_band": band,
            "prob": round(float(prob), 4),
            "chance": float(r.chance),  # unrounded
            "rain_needed_mm": "" if needed is None else needed,
            "reasons": json.dumps(reasons),
            "as_of": a.date,
        })

    out = Path(a.out) if a.out else HERE / "demo" / f"predictions_{a.date}.csv"
    out.parent.mkdir(exist_ok=True)
    with open(out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)

    counts = pd.Series([r["risk_band"] for r in rows]).value_counts()
    print(f"wrote {out}")
    print("bands:", {b: int(counts.get(b, 0)) for b in ["high", "elevated", "low"]})
    need = [r["rain_needed_mm"] for r in rows if r["rain_needed_mm"] != ""]
    print(f"rain_needed_mm: {len(need)} counties not yet high; "
          f"range {min(need):.0f}-{max(need):.0f} mm" if need else "rain_needed_mm: all high")


if __name__ == "__main__":
    main()
