"""
Unit test for the backend's dekad rule (the same rule as the ML repo's
pipeline/dekad.py and the one written out in HANDOFF.md for the app).
Run from the backend folder:

    python -m unittest tests.test_dekad -v

Rule: days 1-10 -> 1st dekad of the month, 11-20 -> 2nd, 21-end -> 3rd.
"""

import datetime
import unittest

import pandas as pd

from make_demo_predictions import dekad_of_year

# (day of month, which dekad of the month it must be)
DAY_TO_PART = [(1, 1), (10, 1), (11, 2), (20, 2), (21, 3), (31, 3)]


class TestDekad(unittest.TestCase):
    def test_boundary_days_in_every_month(self):
        for month in range(1, 13):
            for day, part in DAY_TO_PART:
                if day == 31 and month in (2, 4, 6, 9, 11):
                    continue  # no 31st in this month
                with self.subTest(month=month, day=day):
                    self.assertEqual(dekad_of_year(datetime.date(2023, month, day)),
                                     (month - 1) * 3 + part)

    def test_day_10_is_first_dekad_and_day_20_is_second(self):
        # the old formula (day // 10) got exactly these two wrong
        self.assertEqual(dekad_of_year(datetime.date(2024, 4, 10)), 10)  # not 11
        self.assertEqual(dekad_of_year(datetime.date(2024, 4, 20)), 11)  # not 12

    def test_works_on_pandas_timestamps_and_year_ends(self):
        self.assertEqual(dekad_of_year(pd.Timestamp("2024-04-21")), 12)  # our demo dekad
        self.assertEqual(dekad_of_year(pd.Timestamp("2024-01-01")), 1)
        self.assertEqual(dekad_of_year(pd.Timestamp("2024-12-31")), 36)
        self.assertEqual(dekad_of_year(pd.Timestamp("2024-02-29")), 6)  # leap day


if __name__ == "__main__":
    unittest.main()
