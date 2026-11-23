package com.majialert.kenya.data.util

import kotlin.math.roundToInt

object RiskFormatter {

    fun formatChance(chance: Double?): String {
        if (chance == null || !chance.isFinite() ||
            chance < 0.0 || chance > 1.0
        ) {
            return "Not available"
        }

        val percentage = chance * 100.0

        val rounded = (percentage / 5.0)
            .roundToInt() * 5

        return if (rounded == 0) {
            "Under 5%"
        } else {
            "$rounded%"
        }
    }
}