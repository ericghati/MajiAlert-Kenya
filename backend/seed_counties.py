"""
seed_counties.py - Task 2: create one Firestore document per county.

What it does:
  1. Reads the 47 county names + codes from the boundary file (.gdb).
  2. Writes one document per county at  counties/<admin1Pcode>
     (e.g. counties/KE047 is Nairobi), with a safe starting state:
     "low" risk and no prediction yet.

Safe to run again: it never creates duplicates (same ID = same document).
merge=True means fields we don't list are left alone. But the fields we DO
list below are reset to "low / empty", so re-running after predictions have
been published will wipe those predictions. Run it once at the start.

Usage:   python seed_counties.py [path-to-gdb]
"""

import sys

import geopandas as gpd
from firebase_admin import firestore

from db import db

# Where the county boundaries live. Pass a different path as an argument
# if the file is somewhere else on your computer.
DEFAULT_GDB = (
    r"C:\UNI\YR 3\3.2\Artificial Intelligence\Project"
    r"\KEN_AdminBoundaries_working.gdb"
)
LAYER = "ken_admbnda_adm1_iebc_20191031"  # adm1 = the 47 counties

gdb_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_GDB

# Load only the two columns we need (no geometry needed for names).
counties = gpd.read_file(gdb_path, layer=LAYER)[["admin1Pcode", "admin1Name_en"]]

# Sanity check: Kenya has exactly 47 counties. Stop if the file is wrong.
if len(counties) != 47:
    raise SystemExit(f"Expected 47 counties but found {len(counties)}. Wrong file?")

# A "batch" sends all the writes together, so either all 47 save or none do.
batch = db.batch()
for _, row in counties.iterrows():
    code = row["admin1Pcode"]  # the county ID used everywhere (e.g. KE047)
    ref = db.collection("counties").document(code)
    batch.set(
        ref,
        {
            "name": row["admin1Name_en"],
            "riskBand": "low",  # low / elevated / high
            "chance": None,  # calibrated probability, filled in later
            "reasons": [],  # top-3 plain-language reasons, filled in later
            "rainNeededMm": None,
            "source": "model",  # "model" or "KMD"
            "asOf": None,  # date of the data the prediction is for
            "updatedAt": firestore.SERVER_TIMESTAMP,  # set by Firebase's clock
        },
        merge=True,
    )
batch.commit()

# Read back and count, so we know it really worked.
saved = len(list(db.collection("counties").stream()))
print(f"Seeded {len(counties)} counties. counties collection now has {saved} docs.")
