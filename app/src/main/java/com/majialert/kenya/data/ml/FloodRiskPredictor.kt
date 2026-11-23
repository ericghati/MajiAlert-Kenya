package com.majialert.kenya.data.ml

import com.majialert.kenya.data.model.RainfallInput
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp

data class FloodRiskResult(
    val riskBand: String,
    val calibratedChance: Double,
    val rawProbability: Double
)

object FloodRiskPredictor {

    fun predict(
        input: RainfallInput,
        model: JSONObject
    ): FloodRiskResult {

        val features = FloodFeatureCalculator.calculate(input, model)

        val means = model.getJSONArray("scaler_mean")
        val scales = model.getJSONArray("scaler_scale")
        val coefficients = model.getJSONArray("coefficients")

        val intercept = model.getDouble("intercept")
        val threshold = model.getDouble("threshold")

        require(
            means.length() == features.size &&
                    scales.length() == features.size &&
                    coefficients.length() == features.size
        ) {
            "Model parameter lengths do not match features"
        }

        var logit = intercept

        for (i in features.indices) {
            val mean = means.getDouble(i)
            val scale = scales.getDouble(i)
            val coefficient = coefficients.getDouble(i)

            require(
                mean.isFinite() &&
                        scale.isFinite() &&
                        scale > 0.0 &&
                        coefficient.isFinite()
            ) {
                "Invalid model parameter at index $i"
            }

            val standardized = (features[i] - mean) / scale
            logit += standardized * coefficient
        }

        require(logit.isFinite()) {
            "Invalid model output"
        }

        val rawProbability = if (logit >= 0.0) {
            1.0 / (1.0 + exp(-logit))
        } else {
            val exponential = exp(logit)
            exponential / (1.0 + exponential)
        }

        require(threshold.isFinite() && threshold in 0.5..1.0) {
            "Invalid model threshold"
        }

        val riskBand = when {
            rawProbability >= threshold -> "High"
            rawProbability >= 0.5 -> "Elevated"
            else -> "Low"
        }

        val calibration = model.getJSONObject("calibration")
        val calibratedChance = interpolateCalibration(
            rawProbability,
            calibration.getJSONArray("x"),
            calibration.getJSONArray("y")
        )

        return FloodRiskResult(
            riskBand = riskBand,
            calibratedChance = calibratedChance,
            rawProbability = rawProbability
        )
    }

    private fun interpolateCalibration(
        probability: Double,
        x: JSONArray,
        y: JSONArray
    ): Double {

        require(x.length() == y.length() && x.length() >= 2) {
            "Invalid calibration table"
        }

        val firstX = x.getDouble(0)
        val firstY = y.getDouble(0)

        require(firstX.isFinite() && firstY.isFinite()) {
            "Invalid calibration values"
        }

        var previousX = firstX
        var previousY = firstY

        for (i in 1 until x.length()) {
            val currentX = x.getDouble(i)
            val currentY = y.getDouble(i)

            require(
                currentX.isFinite() &&
                        currentY.isFinite() &&
                        currentX > previousX
            ) {
                "Invalid calibration table ordering"
            }

            if (probability <= currentX) {
                val fraction =
                    ((probability - previousX) /
                            (currentX - previousX)).coerceIn(0.0, 1.0)

                return (previousY +
                        fraction * (currentY - previousY))
                    .coerceIn(0.0, 1.0)
            }

            previousX = currentX
            previousY = currentY
        }

        return previousY.coerceIn(0.0, 1.0)
    }
}