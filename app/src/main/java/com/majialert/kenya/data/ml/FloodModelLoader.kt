package com.majialert.kenya.data.ml

import android.content.Context
import org.json.JSONObject

class FloodModelLoader(private val context: Context) {

    fun loadModel(): JSONObject {
        val json = context.assets.open("model.json")
            .bufferedReader()
            .use { it.readText() }

        val model = JSONObject(json)

        val requiredFields = listOf(
            "features",
            "scaler_mean",
            "scaler_scale",
            "coefficients",
            "intercept",
            "threshold",
            "calibration",
            "normals"
        )

        requiredFields.forEach { field ->
            require(model.has(field)) {
                "Missing model field: $field"
            }
        }

        val featureCount = model.getJSONArray("features").length()

        require(featureCount == 17) {
            "Expected 17 model features, found $featureCount"
        }

        listOf(
            "scaler_mean",
            "scaler_scale",
            "coefficients"
        ).forEach { field ->
            require(model.getJSONArray(field).length() == featureCount) {
                "Invalid $field length"
            }
        }

        return model
    }
}