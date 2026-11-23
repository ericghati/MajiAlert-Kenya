package com.majialert.kenya.data.model

data class RainfallInput(
    val countyCode: String,
    val dekad: Int,
    val currentRainMm: Double,
    val previousRainMm: Double,
    val twoPeriodsAgoRainMm: Double,
    val threeMonthRainMm: Double,
    val nationalAnomalyFraction: Double
) {
    fun isValid(): Boolean {
        return countyCode.matches(Regex("KE\\d{3}")) &&
                dekad in 1..36 &&
                listOf(
                    currentRainMm,
                    previousRainMm,
                    twoPeriodsAgoRainMm,
                    threeMonthRainMm
                ).all { it.isFinite() && it >= 0.0 } &&
                threeMonthRainMm >=
                currentRainMm + previousRainMm + twoPeriodsAgoRainMm &&
                nationalAnomalyFraction.isFinite() &&
                nationalAnomalyFraction in 0.0..1.0
    }
}