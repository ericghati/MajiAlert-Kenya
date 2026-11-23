
import json
import math
from pathlib import Path

model_path = Path(__file__).resolve().parent / "assets" / "model.json"
model = json.loads(model_path.read_text(encoding="utf-8"))

county = "KE006"
dekad = 12
rfh, lag1, lag2 = 50.0, 30.0, 25.0
r3h = 180.0
national_anomaly = 0.4

normals = model["normals"][county]
i = dekad - 1
prev = (i - 1) % 36

rfh_avg = normals["rfh_avg"][i]
r1h = rfh + lag1 + lag2

def percent(rain, normal):
    return 100.0 * (rain + 5.0) / (normal + 5.0)

rfq = percent(rfh, rfh_avg)
r1q = percent(r1h, normals["r1h_avg"][i])
r3q = percent(r3h, normals["r3h_avg"][i])

values = {
    "rfh": rfh,
    "rfh_avg": rfh_avg,
    "rfq": rfq,
    "r1h": r1h,
    "r3h": r3h,
    "r1q": r1q,
    "r3q": r3q,
    "rfh_lag1": lag1,
    "rfh_lag2": lag2,
    "rfq_lag1": percent(lag1, normals["rfh_avg"][prev]),
    "rfh_max": rfh,
    "rfq_max": rfq,
    "r1q_max": r1q,
    "r3q_max": r3q,
    "season_sin": math.sin(2 * math.pi * dekad / 36),
    "season_cos": math.cos(2 * math.pi * dekad / 36),
    "nat_frac_anom": national_anomaly,
}

features = [values[name] for name in model["features"]]

logit = model["intercept"]
for value, mean, scale, coefficient in zip(
        features,
        model["scaler_mean"],
        model["scaler_scale"],
        model["coefficients"]
):
    logit += ((value - mean) / scale) * coefficient

raw = 1 / (1 + math.exp(-logit))

xs = model["calibration"]["x"]
ys = model["calibration"]["y"]

def interpolate(value):
    if value <= xs[0]:
        return ys[0]
    for j in range(1, len(xs)):
        if value <= xs[j]:
            fraction = (value - xs[j-1]) / (xs[j] - xs[j-1])
            return ys[j-1] + fraction * (ys[j] - ys[j-1])
    return ys[-1]

chance = interpolate(raw)
band = (
    "High" if raw >= model["threshold"]
    else "Elevated" if raw >= 0.5
    else "Low"
)

android_chance = 0.058431952662721894

print("Python risk:", band)
print("Python calibrated chance:", chance)
print("Android calibrated chance:", android_chance)
print("Absolute difference:", abs(chance - android_chance))
print("PARITY:", "PASS" if abs(chance - android_chance) < 1e-6 else "FAIL")
