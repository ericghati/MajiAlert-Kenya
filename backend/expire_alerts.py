"""
expire_alerts.py - Task 7: switch off alerts whose time has run out.

Every alert has a validUntil time. Once that time has passed the alert is
no longer valid, so we set active = False. The app only shows alerts where
active is True. publish_predictions.py runs this first, every time.

It never deletes anything: the old alert stays in Firestore as a record,
it is just marked inactive.

Usage:   python expire_alerts.py [--dry-run]
"""

import argparse
import datetime

from google.cloud.firestore_v1.base_query import FieldFilter

from db import db


def expire(dry_run=False):
    """Mark every active alert past its validUntil as inactive.
    Returns how many alerts were expired."""
    now = datetime.datetime.now(datetime.timezone.utc)

    # Only look at alerts that are still switched on.
    active = db.collection("alerts").where(filter=FieldFilter("active", "==", True)).stream()

    batch, in_batch, total = db.batch(), 0, 0
    for doc in active:
        valid_until = doc.to_dict().get("validUntil")
        if valid_until and valid_until <= now:  # time has run out
            total += 1
            if not dry_run:
                batch.update(doc.reference, {"active": False})
                in_batch += 1
                if in_batch == 400:  # a Firestore batch holds at most 500 writes
                    batch.commit()
                    batch, in_batch = db.batch(), 0
    if in_batch and not dry_run:
        batch.commit()
    return total


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="count only, write nothing")
    a = ap.parse_args()
    n = expire(dry_run=a.dry_run)
    print(f"{'Would expire' if a.dry_run else 'Expired'} {n} alert(s).")
