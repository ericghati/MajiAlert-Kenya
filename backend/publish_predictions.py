"""
publish_predictions.py - Task 5: push one dekad's predictions into Firestore.

Reads a predictions CSV (see make_demo_predictions.py) and:
  0. Switches off alerts whose time has run out (expire_alerts.py).
  1. Updates every counties/<code> document: risk band, calibrated chance,
     rain needed, reasons, date.
  2. When a county's band RISES to "high", creates an alerts/<auto-id>
     document (source "model", valid for 10 days) - unless that county
     already has an active model alert, so re-running never duplicates.
  3. Model alerts exist only for "high". When a county is no longer high
     (elevated or low), its active MODEL alerts are switched off.
     KMD alerts are never touched.
  4. Never overwrites a county that has an active KMD advisory:
     official advisories always outrank model predictions.

Usage:
  python publish_predictions.py demo/predictions_2024-04-21.csv
  python publish_predictions.py demo/predictions_2022-03-21.csv --dry-run
"""

import argparse
import csv
import datetime
import json

from firebase_admin import firestore
from google.cloud.firestore_v1.base_query import FieldFilter

from db import db
from expire_alerts import expire

VALID_BANDS = {"low", "elevated", "high"}
ALERT_DAYS = 10  # how long a model alert stays valid


def active_alerts(source):
    """All alerts from `source` ("model" or "KMD") that are switched on.
    Returns a list of (document reference, data)."""
    docs = (
        db.collection("alerts")
        .where(filter=FieldFilter("source", "==", source))
        .where(filter=FieldFilter("active", "==", True))
        .stream()
    )
    return [(d.reference, d.to_dict()) for d in docs]


def still_valid_counties(alerts, now):
    """County codes that have at least one alert whose validUntil is in the future."""
    return {a["county"] for _, a in alerts if a.get("validUntil") and a["validUntil"] > now}


def number_or_none(text):
    """CSV cell -> float, or None when the cell is empty."""
    return float(text) if text.strip() else None


def read_csv(path):
    """Read and check the predictions file. Stops with a clear message if wrong."""
    with open(path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    needed = {"county_code", "risk_band", "prob", "chance", "rain_needed_mm", "reasons", "as_of"}
    if not rows or not needed <= set(rows[0]):
        raise SystemExit(f"CSV must have columns: {sorted(needed)}")
    for r in rows:
        if r["risk_band"] not in VALID_BANDS:
            raise SystemExit(f"{r['county_code']}: bad risk_band {r['risk_band']!r}")
        r["reasons"] = json.loads(r["reasons"])  # text -> real list
        r["chance"] = number_or_none(r["chance"])
        r["rain_needed_mm"] = number_or_none(r["rain_needed_mm"])
        if r["chance"] is not None and not 0 <= r["chance"] <= 1:
            raise SystemExit(f"{r['county_code']}: chance must be between 0 and 1")
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv")
    ap.add_argument("--dry-run", action="store_true", help="show what would change, write nothing")
    a = ap.parse_args()

    rows = read_csv(a.csv)
    now = datetime.datetime.now(datetime.timezone.utc)

    # Step 0: switch off alerts that ran out of time (Task 7).
    expired = expire(dry_run=a.dry_run)

    # What Firestore holds right now, so we can see which bands rose.
    current = {d.id: d.to_dict() for d in db.collection("counties").stream()}
    unknown = [r["county_code"] for r in rows if r["county_code"] not in current]
    if unknown:
        raise SystemExit(f"Not in Firestore (run seed_counties.py?): {unknown}")

    kmd_counties = still_valid_counties(active_alerts("KMD"), now)  # protected: do not touch
    model_alerts = active_alerts("model")
    model_alerted = still_valid_counties(model_alerts, now)  # already have an alert

    batch = db.batch()
    changed = new_alerts = skipped_kmd = deactivated = 0
    for r in rows:
        code, band = r["county_code"], r["risk_band"]

        if code in kmd_counties:  # KMD outranks the model
            skipped_kmd += 1
            continue

        old_band = current[code].get("riskBand")
        as_of = datetime.datetime.strptime(r["as_of"], "%Y-%m-%d").replace(
            tzinfo=datetime.timezone.utc
        )

        # Update the county's public status. The app reads this document.
        batch.set(
            db.collection("counties").document(code),
            {
                "riskBand": band,
                "chance": r["chance"],  # calibrated; the raw prob is never stored
                "reasons": r["reasons"],
                "rainNeededMm": r["rain_needed_mm"],  # None if already high
                "source": "model",
                "asOf": as_of,  # the date the data is for
                "updatedAt": firestore.SERVER_TIMESTAMP,  # when we wrote it
            },
            merge=True,
        )
        changed += old_band != band

        # Alert only when the band RISES to high (not while it stays high),
        # and only if this county has no active model alert already.
        # A county coming back from a KMD advisory counts as "newly high":
        # its old "high" was the advisory's, not the model's.
        rising = old_band != "high" or current[code].get("source") == "KMD"
        if band == "high" and rising and code not in model_alerted:
            name = current[code]["name"]
            batch.set(
                db.collection("alerts").document(),  # empty document() = auto ID
                {
                    "county": code,
                    "severity": "high",
                    "source": "model",
                    "message": f"Flood risk is high in {name} County. "
                               "This is a model estimate, not an official forecast.",
                    "reasons": r["reasons"],
                    "issuedAt": now,
                    "validUntil": now + datetime.timedelta(days=ALERT_DAYS),
                    "active": True,
                },
            )
            new_alerts += 1

        # Model alerts exist only for "high": once a county is elevated or
        # low, its model alerts are switched off.
        # (model_alerts only holds source == "model", so KMD alerts are safe.)
        if band != "high":
            for ref, alert in model_alerts:
                if alert["county"] == code:
                    batch.update(ref, {"active": False})
                    deactivated += 1

    if not a.dry_run:
        batch.commit()  # all writes happen together

    counts = {b: sum(r["risk_band"] == b for r in rows) for b in ["high", "elevated", "low"]}
    print(("DRY RUN - nothing written. " if a.dry_run else "") + f"as of {rows[0]['as_of']}")
    print(f"bands in file : {counts}")
    print(f"counties whose band changed: {changed}")
    print(f"new alerts    : {new_alerts}")
    print(f"model alerts switched off (county not high): {deactivated}")
    print(f"expired by time: {expired}")
    print(f"skipped (active KMD advisory): {skipped_kmd}")


if __name__ == "__main__":
    main()
