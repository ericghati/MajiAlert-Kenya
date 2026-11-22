"""
export_geojson.py - Task 3: make the county map file for the Android app.

The app draws Kenya's 47 counties itself (in a Compose Canvas), so it needs
the county outlines as a small text file. This script:
  1. Reads the detailed county shapes from the boundary file (.gdb).
  2. Repairs the one broken polygon (make_valid).
  3. Simplifies the outlines so the file is small (fewer points per border).
  4. Keeps only: county code, county name, and the shape.
  5. Writes backend/assets/kenya_counties.geojson.

Eric copies the output into app/src/main/assets/.

NOTE: the output file is committed on purpose. The boundary .gdb it is made
from is NOT in this repo (it lives on SKZ's machine), so teammates could not
rebuild it. To regenerate:  python export_geojson.py <path-to-gdb>

Usage:   python export_geojson.py [path-to-gdb]
"""

import sys
from pathlib import Path

import geopandas as gpd

# Same boundary file the seeding script uses.
DEFAULT_GDB = (
    r"C:\UNI\YR 3\3.2\Artificial Intelligence\Project"
    r"\KEN_AdminBoundaries_working.gdb"
)
LAYER = "ken_admbnda_adm1_iebc_20191031"  # adm1 = the 47 counties

# Output goes in backend/assets/ (created if missing).
OUT_FILE = Path(__file__).parent / "assets" / "kenya_counties.geojson"

# How much to simplify, in degrees (~0.01 deg = about 1 km). Bigger number =
# smaller file but blockier borders. 1 km is invisible on a phone-sized map.
SIMPLIFY_TOLERANCE = 0.01

gdb_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_GDB

counties = gpd.read_file(gdb_path, layer=LAYER)

# Make sure coordinates are plain longitude/latitude (EPSG:4326), which is
# what GeoJSON and our app expect.
counties = counties.to_crs(epsg=4326)

# One polygon in the file is invalid (e.g. its border crosses itself).
# make_valid() repairs it so later steps don't break.
counties["geometry"] = counties.geometry.make_valid()

# Remove most of the tiny wiggles in the borders to shrink the file.
# preserve_topology=True stops shapes from collapsing or overlapping.
counties["geometry"] = counties.geometry.simplify(
    SIMPLIFY_TOLERANCE, preserve_topology=True
)

# Keep only what the app needs. admin1Pcode is the county ID everywhere.
counties = counties[["admin1Pcode", "admin1Name_en", "geometry"]]

OUT_FILE.parent.mkdir(exist_ok=True)
# Write with fewer decimals (5 = about 1 metre) to keep the file small.
counties.to_file(OUT_FILE, driver="GeoJSON", COORDINATE_PRECISION=5)

size_kb = OUT_FILE.stat().st_size / 1024
print(f"Wrote {OUT_FILE}")
print(f"Features: {len(counties)}   Size: {size_kb:.0f} KB (target: under 500 KB)")
