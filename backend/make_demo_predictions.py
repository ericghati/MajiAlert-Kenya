"""
make_demo_predictions.py - builds the input CSV for publish_predictions.py.

Until the live prediction engine is validated, we "replay" the past: pick one
dekad (10-day period) from history, score all 47 counties with the saved
model, and write one CSV row per county.

IMPORTANT: it uses backend/assets/model.json, which was trained on
2002-2019 ONLY. Nothing is refitted on the chosen dekad, so replaying
April 2024 is a fair demo, not a model that has "seen" the answer.

Output columns (what publish_predictions.py expects):
  county_code  admin1Pcode, e.g. KE047
  risk_band    low / elevated / high
  prob         the model's raw number. Kept for debugging ONLY: the app must
               never show it to users.
  reasons      JSON list of up to 3 plain sentences (empty for "low")
  as_of        the dekad's start date, YYYY-MM-DD

Usage:
  python make_demo_predictions.py --date 2024-04-11
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

# Plain-language sentence for each feature group. {..} are filled with the
# county's real values for that dekad.
SENTENCES = {
    "this_10_days": "Rain over the last 10 days was {rfq:.0f}% of normal ({rfh:.0f} mm).",
    "past_month": "Rain over the past month was {r1q:.0f}% of normal ({r1h:.0f} mm).",
    "past_3_months": "Rain over the past 3 months was {r3q:.0f}% of normal ({r3h:.0f} mm).",
    "time_of_year": "This time of year is when floods are more likely in this county.",
    "rest_of_kenya": "Heavy rain is widespread across Kenya ({nat:.0f}% of areas well above normal).",
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--date", required=True, help="dekad start date, e.g. 2024-04-11")
    ap.add_argument("--features", default=DEFAULT_FEATURES)
    ap.add_argument("--out", default=None, help="default: demo/predictions_<date>.csv")
    a = ap.parse_args()

    model = json.loads(MODEL_JSON.read_text())
    df = pd.read_csv(a.features, parse_dates=["date"])
    day = df[df.date == a.date].copy()
    if len(day) != 47:
        raise SystemExit(f"Expected 47 counties on {a.date}, found {len(day)}. "
                         "Use a dekad start date (the 1st, 11th or 21st).")

    # ---- the model's arithmetic, exactly as in model.json ----
    # z = (x - mean) / scale ; each feature's push = coef * z
    names = model["features"]
    z = (day[names].values - model["scaler_mean"]) / model["scaler_scale"]
    push = z * np.array(model["coefficients"])  # one column per feature
    logit = model["intercept"] + push.sum(axis=1)
    day["prob"] = 1 / (1 + np.exp(-logit))  # logit -> probability 0..1

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

        reasons = []
        if band != "low":  # a "low" county has nothing to explain
            # the groups pushing risk UP the most, biggest first, at most 3
            top = groups.loc[idx].sort_values(ascending=False).head(3)
            for g, v in top.items():
                if v > 0:
                    reasons.append(SENTENCES[g].format(
                        rfq=r.rfq, rfh=r.rfh, r1q=r.r1q, r1h=r.r1h,
                        r3q=r.r3q, r3h=r.r3h, nat=r.nat_frac_anom * 100))

        rows.append({
            "county_code": r.county,
            "risk_band": band,
            "prob": round(float(prob), 4),
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


if __name__ == "__main__":
    main()
