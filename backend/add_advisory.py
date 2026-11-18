"""
add_advisory.py - Task 6: enter an official KMD (Kenya Meteorological
Department) flood advisory by hand.

KMD advisories always outrank the model. For each county you list, this:
  1. creates an alerts/<auto-id> document with source "KMD";
  2. marks the county as source "KMD", raises its riskBand to the advisory's
     severity if it was lower (it is never lowered), and clears the
     model-only fields (chance, rainNeededMm) and the model's reasons, so
     the app does not show model numbers under an official advisory.
publish_predictions.py then leaves these counties alone until the advisory
expires.

Usage (dates are inclusive, UTC):
  python add_advisory.py --counties KE047 KE022 --severity high \\
      --valid-from 2024-04-21 --valid-until 2024-04-28 \\
      --message "Heavy rain expected over Nairobi and Kiambu. Avoid flooded roads."
  add --dry-run to check everything without writing.
"""

import argparse
import datetime

from firebase_admin import firestore

from db import db

SEVERITIES = ["elevated", "high"]
RANK = {"low": 0, "elevated": 1, "high": 2}


def parse_day(text, name):
    """'2024-04-21' -> a date at 00:00 UTC. Stops with a clear message if wrong."""
    try:
        d = datetime.datetime.strptime(text, "%Y-%m-%d")
    except ValueError:
        raise SystemExit(f"{name} must look like 2024-04-21, got {text!r}")
    return d.replace(tzinfo=datetime.timezone.utc)


def main():
    ap = argparse.ArgumentParser(description="Add a manual KMD flood advisory.")
    ap.add_argument("--counties", nargs="+", required=True, help="county codes, e.g. KE047 KE022")
    ap.add_argument("--severity", required=True, choices=SEVERITIES)
    ap.add_argument("--valid-from", required=True, help="first day, YYYY-MM-DD")
    ap.add_argument("--valid-until", required=True, help="last day (included), YYYY-MM-DD")
    ap.add_argument("--message", required=True, help="text shown to users")
    ap.add_argument("--dry-run", action="store_true", help="check only, write nothing")
    a = ap.parse_args()

    # ---- check the input before touching the database ----
    now = datetime.datetime.now(datetime.timezone.utc)
    start = parse_day(a.valid_from, "--valid-from")
    # "until" is inclusive, so the advisory runs to the END of that day.
    until = parse_day(a.valid_until, "--valid-until") + datetime.timedelta(days=1)
    if start > now:
        raise SystemExit("--valid-from is in the future; use today or an earlier date.")
    if until <= now:
        raise SystemExit("--valid-until has already passed, so this advisory would be expired.")
    if until <= start:
        raise SystemExit("--valid-until must not be before --valid-from.")
    message = a.message.strip()
    if not message:
        raise SystemExit("--message cannot be empty.")
    if len(message) > 300:
        raise SystemExit(f"--message is {len(message)} characters; keep it under 300.")

    codes = list(dict.fromkeys(c.strip().upper() for c in a.counties))  # drop repeats
    current = {}
    for code in codes:
        snap = db.collection("counties").document(code).get()
        if not snap.exists:
            raise SystemExit(f"Unknown county code {code}. Codes look like KE047.")
        current[code] = snap.to_dict()

    # ---- write: one alert per county + update the county ----
    batch = db.batch()
    for code in codes:
        batch.set(
            db.collection("alerts").document(),  # auto ID
            {
                "county": code,
                "severity": a.severity,
                "source": "KMD",
                "message": message,
                "reasons": [],
                "issuedAt": start,
                "validUntil": until,
                "active": True,
            },
        )
        old_band = current[code].get("riskBand", "low")
        batch.set(
            db.collection("counties").document(code),
            {
                "source": "KMD",
                # raise to the advisory's severity, never lower
                "riskBand": a.severity if RANK[a.severity] > RANK.get(old_band, 0) else old_band,
                "chance": None,  # model numbers do not apply to an official advisory
                "rainNeededMm": None,
                "reasons": ["Official advisory issued by the Kenya Meteorological Department (KMD)."],
                "updatedAt": firestore.SERVER_TIMESTAMP,
            },
            merge=True,
        )

    if not a.dry_run:
        batch.commit()
    names = [current[c]["name"] + " (" + c + ")" for c in codes]
    print(("DRY RUN - nothing written. " if a.dry_run else "") +
          f"KMD advisory ({a.severity}) for {len(codes)} county(ies): {', '.join(names)}")
    print(f"valid {start:%Y-%m-%d} to {a.valid_until} (inclusive)")


if __name__ == "__main__":
    main()
