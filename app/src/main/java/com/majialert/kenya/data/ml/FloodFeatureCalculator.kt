package com.majialert.kenya.data.ml

import com.majialert.kenya.data.model.RainfallInput
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object FloodFeatureCalculator {

    fun calculate(
        input: RainfallInput,
        model: JSONObject
    ): DoubleArray {

        require(input.isValid()) {
            "Invalid rainfall inputs"
        }

        val normals = model.getJSONObject("normals")
            .getJSONObject(input.countyCode)

        val index = input.dekad - 1
        val previousIndex = (index + 35) % 36

        fun normal(name: String, position: Int): Double {
            val value = normals.getJSONArray(name).getDouble(position)
            require(value.isFinite() && value >= 0.0) {
                "Invalid rainfall normal: $name"
            }
            return value
        }

        val rfh = input.currentRainMm
        val lag1 = input.previousRainMm
        val lag2 = input.twoPeriodsAgoRainMm
        val r3h = input.threeMonthRainMm

        val r1h = rfh + lag1 + lag2

        val rfhAvg = normal("rfh_avg", index)
        val rfhLag1Avg = normal("rfh_avg", previousIndex)
        val r1hAvg = normal("r1h_avg", index)
        val r3hAvg = normal("r3h_avg", index)

        fun percentOfNormal(rain: Double, average: Double): Double {
            return 100.0 * (rain + 5.0) / (average + 5.0)
        }

        val rfq = percentOfNormal(rfh, rfhAvg)
        val rfqLag1 = percentOfNormal(lag1, rfhLag1Avg)
        val r1q = percentOfNormal(r1h, r1hAvg)
        val r3q = percentOfNormal(r3h, r3hAvg)

        val angle = 2.0 * PI * input.dekad / 36.0

        val values = mapOf(
            "rfh" to rfh,
            "rfh_avg" to rfhAvg,
            "rfq" to rfq,
            "r1h" to r1h,
            "r3h" to r3h,
            "r1q" to r1q,
            "r3q" to r3q,
            "rfh_lag1" to lag1,
            "rfh_lag2" to lag2,
            "rfq_lag1" to rfqLag1,

            // County-level approximations documented in model.json
            "rfh_max" to rfh,
            "rfq_max" to rfq,
            "r1q_max" to r1q,
            "r3q_max" to r3q,

            "season_sin" to sin(angle),
            "season_cos" to cos(angle),
            "nat_frac_anom" to input.nationalAnomalyFraction
        )

        val featureNames = model.getJSONArray("features")

        return DoubleArray(featureNames.length()) { i ->
            val name = featureNames.getString(i)
            val value = values[name]
                ?: error("Unknown model feature: $name")

            require(value.isFinite()) {
                "Non-finite feature: $name"
            }

            value
        }
    }
}
