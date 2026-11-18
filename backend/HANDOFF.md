# MajiAlert backend: handoff for the Android app

For Eric (Android / Compose). Everything here is what the backend produces and what
the app has to do with it. Backend owner: SKZ. Ask SKZ if anything here is unclear or
does not match what you see in Firestore.

## 1. What you get, and where it is

| File | What it is | What to do |
|---|---|---|
| `backend/assets/model.json` (84 KB) | The flood model as numbers, plus per-county rainfall normals, the calibration table and the training range of every feature | Copy to `app/src/main/assets/` |
| `backend/assets/kenya_counties.geojson` (76 KB) | 47 county outlines for the Map screen | Copy to `app/src/main/assets/` |
| Firestore project `majialert-44663` | Live county status and alerts (read only for the app) | Read with the Firebase SDK |
| `backend/firestore.rules` | The security rules already published | Nothing to do |

`app/google-services.json` **is committed**, so the project builds after a pull. The Firebase key
inside it is restricted to package `com.majialert.kenya` **and to registered debug SHA-1
fingerprints**. A build signed with your own debug keystore has a different SHA-1, so until it is
registered, Firebase calls fail with "API key not valid". Run `gradlew signingReport`, copy the
`SHA1` of the `debug` variant and send it to SKZ to add. (Never commit anything named
`serviceAccountKey.json` or `*-adminsdk-*.json`: those are admin keys. The `.gitignore` blocks them.)

`model.json` has `source` (the ML repo commit that made it) and `exported` (date). If SKZ
sends a new `model.json`, just replace the file: the format is versioned (`"version": 1`).

## 2. Four rules that must hold everywhere

1. **Never show a raw probability.** The model's raw number (`prob` in section 5) is
   inflated on purpose and is NOT a chance of flooding. It is not even stored in
   Firestore. Show the **band** and the **rounded chance** only.
2. **Round the chance for display.** Firestore holds the calibrated chance **unrounded** (for
   example `0.4179`); the app does the rounding, to the nearest 5%:
   `shown = floor(chance / 0.05 + 0.5) * 0.05`. Under 2.5% rounds to 0: display that as
   **"under 5%"**, never "0%". Apply the same rule to the Check risk result (section 5, step 4).
3. **KMD advisories outrank the model.** If `source == "KMD"`, show it as an official
   advisory from the Kenya Meteorological Department and do not show model numbers.
4. **Wording:** model results are "a model estimate, not an official forecast".

## 3. Firestore (the app only reads)

### `counties/{admin1Pcode}`: 47 docs, ID = county code (`KE047` = Nairobi)

| Field | Type | Meaning |
|---|---|---|
| `name` | string | "Nairobi" |
| `riskBand` | string | `low` / `elevated` / `high`. Colour the map with this |
| `chance` | number or null | Calibrated chance of flooding, 0 to 1, **not rounded**: round it for display (rule 2). `null` when `source == "KMD"` |
| `reasons` | array of strings | Model: exactly 3 plain sentences. KMD: one sentence saying it is an official advisory |
| `rainNeededMm` | number or null | 10-day rain total (mm) that would make the county "high". `null` if already high or KMD |
| `source` | string | `model` or `KMD` |
| `asOf` | timestamp | The date the data is for (see section 7) |
| `updatedAt` | timestamp | When the backend wrote it |

Example (Nairobi): `riskBand: "high"`, `chance: 0.4179` (shown as 40%), `source: "model"`,
`reasons[0]: "Rain over the past month was 311% of normal (536 mm), which raises the flood risk."`

### `alerts/{autoId}`

| Field | Type | Meaning |
|---|---|---|
| `county` | string | County code |
| `severity` | string | `elevated` or `high` |
| `source` | string | `model` or `KMD` |
| `message` | string | Text to show |
| `reasons` | array | Model alerts: the 3 reasons. KMD alerts: empty |
| `issuedAt`, `validUntil` | timestamp | Validity window |
| `active` | bool | Show only `active == true` |

- **Model alerts exist only while a county is `high`.** When it is no longer high (elevated or
  low) the backend sets `active = false`. They also expire after 10 days.
- KMD alerts are entered by hand and last until their `validUntil`.
- Query: `alerts.whereEqualTo("active", true)`. Sort and group in the app.
- Use snapshot listeners so the map and alerts update live.

`subcounties/{admin2Pcode}` is not built.

## 4. Map screen

`kenya_counties.geojson` is a standard FeatureCollection in longitude/latitude (EPSG:4326).

- 47 features, geometry type `Polygon` or `MultiPolygon` (islands such as Lamu are multi).
- Properties: `admin1Pcode` (the county ID, same as the Firestore doc ID) and `admin1Name_en`.
- Draw each polygon on a Compose `Canvas`. Equirectangular is fine for Kenya:
  `x = lon`, `y = -lat`, then scale to fit (lon 33.9 to 41.9, lat -4.7 to 5.4).
- Fill colour from `counties/{admin1Pcode}.riskBand`. Tap a county to show its `chance`,
  `reasons`, `rainNeededMm` and the `asOf` date.

## 5. Check risk screen: the exact scoring arithmetic

Everything below uses only `model.json`. Nothing else is needed. The same arithmetic is in
`pipeline/export_model.py` in the ML repo (`score`, `calibrate`, `display_chance`).

### Inputs from the user

County, dekad of year, three 10-day rain totals (mm) `rfh` (newest), `rfh_lag1`, `rfh_lag2`,
the 3-month total `r3h` (mm), and a "how widespread" slider from 0 to 1.

**Dekad of year (1 to 36):** `(month - 1) * 3 + min((day - 1) / 10, 2) + 1` using integer division.

- Days **1 to 10** are the 1st dekad of the month, **11 to 20** the 2nd, **21 to the end** the 3rd.
- Example: 21 April gives `3*3 + 2 + 1 = 12`. 1 January is dekad 1, 31 December is dekad 36.
- Unit-test it. For January the days 1, 10, 11, 20, 21 and 31 must give dekads 1, 1, 2, 2, 3, 3
  (in April: 10, 10, 11, 11, 12, 12 for days 1, 10, 11, 20, 21, 30). Day 10 and day 20 are the ones
  an easy `day / 10` gets wrong.
- List indexes in `normals` are `dekad - 1`.
- A date picker works, or let the user pick the month and early / mid / late (part 1, 2 or 3).

### Step 1: build the 17 features

`n = model.normals[county]` (it has `rfh_avg`, `r1h_avg`, `r3h_avg`, each a list of 36).

| Feature | How |
|---|---|
| `rfh`, `rfh_lag1`, `rfh_lag2`, `r3h` | the user's inputs |
| `r1h` | `rfh + rfh_lag1 + rfh_lag2` (exact) |
| `rfh_avg` | `n.rfh_avg[dekad-1]` (exact) |
| `season_sin`, `season_cos` | `sin(2*pi*dekad/36)`, `cos(2*pi*dekad/36)` (exact) |
| `rfq` | `100 * rfh / n.rfh_avg[dekad-1]` |
| `rfq_lag1` | `100 * rfh_lag1 / n.rfh_avg[prev-1]` where `prev = dekad - 1`, and dekad 1's previous is 36 |
| `r1q` | `100 * r1h / n.r1h_avg[dekad-1]` |
| `r3q` | `100 * r3h / n.r3h_avg[dekad-1]` |
| `rfh_max`, `rfq_max`, `r1q_max`, `r3q_max` | **approximated: set equal to `rfh`, `rfq`, `r1q`, `r3q`** |
| `nat_frac_anom` | **approximated: the slider value** (0 = nowhere in Kenya well above normal, 1 = everywhere) |

### Step 2: score

Go through `model.features` in order. For feature `i` with value `x`:

```
z[i]       = (x - scaler_mean[i]) / scaler_scale[i]
push[i]    = coefficients[i] * z[i]
logit      = intercept + sum(push)
prob       = 1 / (1 + e^-logit)          // raw, NEVER shown
```

### Step 3: band

```
band = "high"      if prob >= model.threshold      // 0.646
       "elevated"  if prob >= 0.5
       "low"       otherwise
```

### Step 4: chance (calibration), then round

`calibration.x` and `calibration.y` are two lists of the same length (a lookup table, x is
increasing). Straight-line interpolation, clipped at both ends:

```
if prob <= x[0]:  chance = y[0]
elif prob >= x[-1]: chance = y[-1]
else: find i with x[i-1] <= prob < x[i];  
      chance = y[i-1] + (y[i] - y[i-1]) * (prob - x[i-1]) / (x[i] - x[i-1])
shown = min(1, floor(chance / 0.05 + 0.5) * 0.05)      // display_rounding = 0.05
```

### Step 5: the reasons (explainable AI)

For each group in `model.groups` (`this_10_days`, `past_month`, `past_3_months`,
`time_of_year`, `rest_of_kenya`): `groupPush = sum of push[i]` over the features in that group.
Positive pushes the risk up, negative holds it down.

- **High or elevated result:** take the 3 groups with the largest `groupPush`.
- **Low result:** take the 3 with the smallest (most negative): this explains why it is NOT flooding.
- One sentence per group, saying which way it pushes, with the real values. The exact wording is
  in `SENTENCES` in `backend/make_demo_predictions.py`; use the same text so the app and the
  database agree.
- **Skip a rain group whose direction contradicts its own figure** (for example "82% of
  normal ... raises the risk"): a rain group (`this_10_days` uses `rfq`, `past_month` uses
  `r1q`, `past_3_months` uses `r3q`) is only worded "raises" if its figure is 100 or more, and
  only "keeps down" if it is under 100. Take the next group instead.

### Test vectors (use as unit tests; computed from `model.json` with this exact arithmetic)

**A: wet.** County `KE047`, dekad 12, `rfh=130`, `rfh_lag1=160`, `rfh_lag2=90`, `r3h=536`, slider `0.40`.

- Derived: `r1h=380`, `rfh_avg=61.614`, `rfq=210.991`, `rfq_lag1=321.385`, `r1q=224.835`, `r3q=177.13`.
- `logit = 3.523079`, `prob = 0.971337`, band **high**, calibrated chance `0.417910`, **shown 40%**.
- Group pushes: this_10_days `0.0903`, past_month `2.5868`, past_3_months `0.5081`, time_of_year `0.4020`, rest_of_kenya `0.6353`.
- Reasons for a high result: past_month, rest_of_kenya, past_3_months (the 3 largest).
- Tier-2 warnings: none.

**B: dry.** County `KE047`, dekad 7, `rfh=5`, `rfh_lag1=8`, `rfh_lag2=3`, `r3h=40`, slider `0.02`.

- Derived: `r1h=16`, `rfh_avg=29.486`, `rfq=16.957`, `rfq_lag1=85.025`, `r1q=28.792`, `r3q=21.329`.
- `logit = -1.631948`, `prob = 0.163564`, band **low**, calibrated chance `0.006205`, shown 0, so display **"under 5%"**.
- Group pushes: this_10_days `0.0468`, past_month `-0.8651`, past_3_months `-0.6689`, time_of_year `0.6408`, rest_of_kenya `-0.0862`.
- Reasons for a low result: past_month, past_3_months, rest_of_kenya (the 3 most negative).
- **Tier-2 warning:** `r3q = 21.33` is below its training minimum `22.06` (the 3-month rain is unusually low), so the result is shown WITH a warning. `r3q_max` repeats it (same value).

**C: extreme storm (warning test).** County `KE047`, dekad 12, `rfh=450`, `rfh_lag1=160`, `rfh_lag2=90`, `r3h=700`, slider `0.40`.

- Not rejected (all under the Tier-1 limits), but warnings on: `rfh = 450` (training max 403.6), `r1h = 700` (max 698.2), `rfq = 730.4` (max 591.1), plus the copies `rfh_max` and `rfq_max` (max 613.4). Report it as one warning about this 10 days' rain.

If your numbers differ in the 4th decimal, check the dekad index and the previous-dekad wrap first.

### Input validation (needed for the assignment marks): two tiers

**Tier 1: reject** (do not compute; show a clear message next to the field):
- empty, not a number, or negative in any rain field;
- `r3h` smaller than `rfh + rfh_lag1 + rfh_lag2` (the 3-month total includes those dekads);
- `rfh`, `rfh_lag1` or `rfh_lag2` above 600 mm, or `r3h` above 3000 mm (not physically credible; these limits are a suggestion).

**Tier 2: warn, do not reject.** `model.json` has `training_range`: for each of the 17
features, the `min` and `max` seen in the training years (2002 to 2019). After building the
features in step 1, compare each feature `v` with its range. If `v < min` or `v > max`, still
show the result, with a visible warning such as *"This is outside what the model has learned
from (rain up to 404 mm in 10 days). Treat the result with caution."* The result is an
extrapolation there.

Name the input that caused it. Features map to what the user typed like this:

| Features | Tell the user it is |
|---|---|
| `rfh`, `rfq`, `rfh_max`, `rfq_max` | this 10 days' rain |
| `rfh_lag1`, `rfq_lag1` | the previous 10 days' rain |
| `rfh_lag2` | the rain 20 days ago |
| `r1h`, `r1q`, `r1q_max` | the past-month total (the three 10-day values together) |
| `r3h`, `r3q`, `r3q_max` | the 3-month total |
| `nat_frac_anom` | the "how widespread" slider (training maximum 0.904) |
| `rfh_avg`, `season_sin`, `season_cos` | cannot go out of range: they come from the county and dekad |

One exception: **do not warn on the LOW side for the millimetre features** (`rfh`, `rfh_lag1`,
`rfh_lag2`, `rfh_max`, `r1h`, `r3h`). Their training minimums are about 0 mm (a county average is never
exactly 0, so `rfh` starts at 0.001), and "no rain" is a valid answer. Warn on the high side
for those, and on both sides for the percent-of-normal features and the slider.

Headline numbers: `rfh` up to 403.6 mm, `r3h` up to 1364.9 mm, `rfq` up to 591% of normal.
Read the real limits from `training_range` in `model.json`; do not hard-code them. The tiers
mean a typo like -5 or "abc" is rejected, while a real but extreme storm of 450 mm is accepted with a warning.

## 6. What is approximated, and how much it matters

- `rfh_max`, `rfq_max`, `r1q_max`, `r3q_max` are really the wettest sub-county in the county.
  The app only has one county value, so it uses the county value.
- `nat_frac_anom` is really the share of Kenya's sub-counties above 200% of normal. The slider stands in for it.
- `rfq`, `r1q`, `r3q` are ratio of the county's mean rain to the county's mean normal. The
  model was trained on the mean of each sub-county's ratio. Average difference: `rfq` 12, `r1q` 4, `r3q` 1 points.
- Tested on 30,456 training-year county dekads: mean probability difference 0.008; the
  high/not-high decision differs on 0.52% of rows (with the slider set perfectly).

Say this on the screen ("an approximation from the numbers you entered") and in the demo.

## 7. About the data in Firestore right now

The live prediction engine is not wired in yet. The database is a **replay of one past dekad
with the real model**: currently **2024-04-21** (almost every county comes out high; real
flooding was reported in about 14 counties, which is the recall-first design of the model).
The model was trained on 2002 to 2019 only, so 2024 is data it never saw. Show `asOf` on screen so the
date is never mistaken for today.

SKZ can switch it in one command, for example to a dry dekad (`2022-03-21`: 35 low, 12 elevated, 0 high).

## 8. Backend scripts (for reference; SKZ runs these)

| Script | What it does |
|---|---|
| `seed_counties.py` | Creates the 47 `counties` docs |
| `make_demo_predictions.py --date YYYY-MM-DD` | Scores a past dekad into `demo/predictions_<date>.csv` |
| `publish_predictions.py <csv>` | Writes the CSV to Firestore, creates/switches off model alerts, never touches counties with an active KMD advisory |
| `add_advisory.py` | Enters an official KMD advisory by hand |
| `expire_alerts.py` | Switches off alerts past `validUntil` (also runs at the start of every publish) |
| `export_geojson.py` | Rebuilds `kenya_counties.geojson` |
| `firestore.rules` | Read-only for the app, write only by the backend |

Push notifications (FCM) are not built yet. Nothing in the app needs them for now.
