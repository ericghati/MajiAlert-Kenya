"""
publish_predictions.py - Task 5: push one dekad's predictions into Firestore.

Reads a predictions CSV (see make_demo_predictions.py) and:
  1. Updates every counties/<code> document: risk band, reasons, date.
  2. When a county's band RISES to "high", creates an alerts/<auto-id>
     document (source "model", valid for 10 days) - unless that county
     already has an active model alert, so re-running never duplicates.
  3. Never overwrites a county that has an active KMD advisory:
     official advisories always outrank model predictions.

Usage:
  python publish_predictions.py demo/predictions_2024-04-11.csv
  python publish_predictions.py demo/predictions_2022-03-21.csv --dry-run
"""

import argparse
import csv
import datetime
import json

from firebase_admin import firestore
from google.cloud.firestore_v1.base_query import FieldFilter

from db import db

VALID_BANDS = {"low", "elevated", "high"}
ALERT_DAYS = 10  # how long a model alert stays valid


def active_alert_counties(source, now):
    """Set of county codes that have a still-valid, active alert from `source`."""
    docs = (
        db.collection("alerts")
        .where(filter=FieldFilter("source", "==", source))
        .where(filter=FieldFilter("active", "==", True))
        .stream()
    )
    out = set()
    for d in docs:
        a = d.to_dict()
        # validUntil is checked here (not in the query) to avoid needing
        # a special Firestore index.
        if a.get("validUntil") and a["validUntil"] > now:
            out.add(a["county"])
    return out


def read_csv(path):
    """Read and check the predictions file. Stops with a clear message if wrong."""
    with open(path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    needed = {"county_code", "risk_band", "prob", "reasons", "as_of"}
    if not rows or not needed <= set(rows[0]):
        raise SystemExit(f"CSV must have columns: {sorted(needed)}")
    for r in rows:
        if r["risk_band"] not in VALID_BANDS:
            raise SystemExit(f"{r['county_code']}: bad risk_band {r['risk_band']!r}")
        r["reasons"] = json.loads(r["reasons"])  # text -> real list
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv")
    ap.add_argument("--dry-run", action="store_true", help="show what would change, write nothing")
    a = ap.parse_args()

    rows = read_csv(a.csv)
    now = datetime.datetime.now(datetime.timezone.utc)

    # TODO (Task 7): call expire_alerts here, so old alerts are switched off first.

    # What Firestore holds right now, so we can see which bands rose.
    current = {d.id: d.to_dict() for d in db.collection("counties").stream()}
    unknown = [r["county_code"] for r in rows if r["county_code"] not in current]
    if unknown:
        raise SystemExit(f"Not in Firestore (run seed_counties.py?): {unknown}")

    kmd_counties = active_alert_counties("KMD", now)  # protected: do not touch
    model_alerted = active_alert_counties("model", now)  # already have an alert

    batch = db.batch()
    changed = new_alerts = skipped_kmd = 0
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
                "chance": None,  # no calibrated chance yet; the raw prob is never stored
                "reasons": r["reasons"],
                "rainNeededMm": None,
                "source": "model",
                "asOf": as_of,  # the date the data is for
                "updatedAt": firestore.SERVER_TIMESTAMP,  # when we wrote it
            },
            merge=True,
        )
        changed += old_band != band

        # Alert only when the band RISES to high (not while it stays high),
        # and only if this county has no active model alert already.
        if band == "high" and old_band != "high" and code not in model_alerted:
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

    if not a.dry_run:
        batch.commit()  # all writes happen together

    counts = {b: sum(r["risk_band"] == b for r in rows) for b in ["high", "elevated", "low"]}
    print(("DRY RUN - nothing written. " if a.dry_run else "") + f"as of {rows[0]['as_of']}")
    print(f"bands in file : {counts}")
    print(f"counties whose band changed: {changed}")
    print(f"new alerts    : {new_alerts}")
    print(f"skipped (active KMD advisory): {skipped_kmd}")


if __name__ == "__main__":
    main()
