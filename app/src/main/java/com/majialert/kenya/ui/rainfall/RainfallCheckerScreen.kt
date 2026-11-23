
package com.majialert.kenya.ui.rainfall

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.majialert.kenya.data.model.RainfallInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import com.majialert.kenya.data.ml.FloodModelLoader
import com.majialert.kenya.data.ml.FloodRiskPredictor
import com.majialert.kenya.data.ml.FloodRiskResult
import com.majialert.kenya.data.util.RiskFormatter
import com.majialert.kenya.data.model.CountyData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RainfallCheckerScreen() {

    var currentRain by remember { mutableStateOf("") }
    var previousRain by remember { mutableStateOf("") }
    var twoPeriodsAgoRain by remember { mutableStateOf("") }
    var threeMonthRain by remember { mutableStateOf("") }

    var countyCode by remember { mutableStateOf("KE006") }

    var countyDropdownExpanded by remember {
        mutableStateOf(false)
    }

    val counties = CountyData.counties

    val selectedCounty = counties.firstOrNull {
        it.code == countyCode
    }

    var dekadText by remember { mutableStateOf("12") }

    var dekadDropdownExpanded by remember {
        mutableStateOf(false)
    }

    val monthNames = listOf(
        "January", "February", "March", "April",
        "May", "June", "July", "August",
        "September", "October", "November", "December"
    )

    val dekadOptions = monthNames.flatMapIndexed { monthIndex, month ->
        listOf(
            Triple(monthIndex * 3 + 1, month, "1–10"),
            Triple(monthIndex * 3 + 2, month, "11–20"),
            Triple(monthIndex * 3 + 3, month, "21–end")
        )
    }

    val selectedDekad = dekadOptions.firstOrNull {
        it.first == dekadText.toIntOrNull()
    }

    var nationalAnomaly by remember { mutableFloatStateOf(0.4f) }

    var validationError by remember { mutableStateOf<String?>(null) }
    var validatedInput by remember { mutableStateOf<RainfallInput?>(null) }

    val context = LocalContext.current

    var predictionResult by remember {
        mutableStateOf<FloodRiskResult?>(null)
    }

    var predictionInputSnapshot by remember {
        mutableStateOf<RainfallInput?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Rainfall Risk Checker",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Explore a hypothetical flood-risk scenario using historical rainfall patterns. This is not a live forecast or official flood warning.",
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        Text(
            text = "Location and time period",
            style = MaterialTheme.typography.titleMedium
        )

        ExposedDropdownMenuBox(
            expanded = countyDropdownExpanded,
            onExpandedChange = {
                countyDropdownExpanded = !countyDropdownExpanded
            }
        ) {
            OutlinedTextField(
                value = selectedCounty?.name ?: "Select county",
                onValueChange = {},
                readOnly = true,
                label = { Text("County") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = countyDropdownExpanded
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = countyDropdownExpanded,
                onDismissRequest = {
                    countyDropdownExpanded = false
                }
            ) {
                counties.forEach { county ->
                    DropdownMenuItem(
                        text = { Text(county.name) },
                        onClick = {
                            countyCode = county.code
                            countyDropdownExpanded = false
                        }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = dekadDropdownExpanded,
            onExpandedChange = {
                dekadDropdownExpanded = !dekadDropdownExpanded
            }
        ) {
            OutlinedTextField(
                value = selectedDekad?.let {
                    "${it.second} ${it.third}"
                } ?: "Select rainfall period",
                onValueChange = {},
                readOnly = true,
                label = { Text("Rainfall period") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = dekadDropdownExpanded
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = dekadDropdownExpanded,
                onDismissRequest = {
                    dekadDropdownExpanded = false
                }
            ) {
                dekadOptions.forEach { (number, month, days) ->
                    DropdownMenuItem(
                        text = {
                            Text("$month $days")
                        },
                        onClick = {
                            dekadText = number.toString()
                            dekadDropdownExpanded = false
                        }
                    )
                }
            }
        }


        Text(
            text = "National Rainfall Conditions",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "For this hypothetical scenario, estimate how widespread " +
                    "well-above-normal rainfall is across Kenya.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Assumed widespread rainfall: " +
                    "${(nationalAnomaly * 100).toInt()}%",
            style = MaterialTheme.typography.titleSmall
        )

        Slider(
            value = nationalAnomaly,
            onValueChange = { nationalAnomaly = it },
            valueRange = 0f..1f,
            steps = 19
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0% — None", style = MaterialTheme.typography.bodySmall)
            Text("100% — Nationwide", style = MaterialTheme.typography.bodySmall)
        }

        Text(
            text = "This is an assumed input, not live weather data. " +
                    "It describes the extent of unusually high rainfall, " +
                    "not the percentage of Kenya experiencing flooding.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )


        HorizontalDivider()

        Text(
            text = "Rainfall measurements",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = currentRain,
            onValueChange = { currentRain = it },
            label = { Text("Current 10-day rainfall (mm)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = previousRain,
            onValueChange = { previousRain = it },
            label = { Text("Previous 10-day rainfall (mm)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = twoPeriodsAgoRain,
            onValueChange = { twoPeriodsAgoRain = it },
            label = { Text("Earlier 10-day rainfall (mm)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = threeMonthRain,
            onValueChange = { threeMonthRain = it },
            label = { Text("Total rainfall over 3 months (mm)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                validatedInput = null
                validationError = null
                predictionResult = null
                predictionInputSnapshot = null

                val current = currentRain.toDoubleOrNull()
                val previous = previousRain.toDoubleOrNull()
                val earlier = twoPeriodsAgoRain.toDoubleOrNull()
                val threeMonth = threeMonthRain.toDoubleOrNull()
                val dekad = dekadText.toIntOrNull()

                if (
                    current == null ||
                    previous == null ||
                    earlier == null ||
                    threeMonth == null ||
                    dekad == null
                ) {
                    validationError = "Please enter valid numbers in all fields."
                } else {
                    val input = RainfallInput(
                        countyCode = countyCode.trim(),
                        dekad = dekad,
                        currentRainMm = current,
                        previousRainMm = previous,
                        twoPeriodsAgoRainMm = earlier,
                        threeMonthRainMm = threeMonth,
                        nationalAnomalyFraction = nationalAnomaly.toDouble()
                    )

                    if (!input.isValid()) {
                        validationError =
                            "Check your county code, period (1–36), and rainfall values. " +
                                    "Rainfall cannot be negative, and the 3-month total must " +
                                    "be at least the sum of the three 10-day periods."
                    } else {
                        try {
                            val model = FloodModelLoader(context).loadModel()

                            require(model.getJSONObject("normals").has(input.countyCode)) {
                                "County not found in the trained model."
                            }

                            predictionResult = FloodRiskPredictor.predict(
                                input = input,
                                model = model
                            )

                            predictionInputSnapshot = input
                            validatedInput = input

                        } catch (e: Exception) {
                            validationError =
                                "Unable to calculate risk: ${e.message ?: "Unknown error"}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Calculate Risk")
        }

        validationError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Start
            )
        }

        val currentInputSnapshot = RainfallInput(
            countyCode = countyCode.trim(),
            dekad = dekadText.toIntOrNull() ?: -1,
            currentRainMm = currentRain.toDoubleOrNull() ?: Double.NaN,
            previousRainMm = previousRain.toDoubleOrNull() ?: Double.NaN,
            twoPeriodsAgoRainMm = twoPeriodsAgoRain.toDoubleOrNull()
                ?: Double.NaN,
            threeMonthRainMm = threeMonthRain.toDoubleOrNull()
                ?: Double.NaN,
            nationalAnomalyFraction = nationalAnomaly.toDouble()
        )

        val inputsChanged =
            predictionInputSnapshot != null &&
                    predictionInputSnapshot != currentInputSnapshot

        predictionResult
            ?.takeUnless { inputsChanged }
            ?.let { result ->

            HorizontalDivider()

            Text(
                text = "Historical Scenario Result",
                style = MaterialTheme.typography.titleLarge
            )

            val riskColor = when (result.riskBand) {
                "High" -> MaterialTheme.colorScheme.error
                "Elevated" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }

            val riskExplanation = when (result.riskBand) {
                "High" ->
                    "The model score exceeds its high-risk threshold " +
                            "under the rainfall conditions you entered."

                "Elevated" ->
                    "The model score indicates elevated risk, " +
                            "but remains below its high-risk threshold."

                else ->
                    "The model score is below its elevated-risk " +
                            "threshold for this rainfall scenario."
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )


            {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Text(
                        text = "${result.riskBand} Risk",
                        style = MaterialTheme.typography.headlineMedium,
                        color = riskColor
                    )

                    Text(
                        text = RiskFormatter.formatChance(
                            result.calibratedChance
                        ),
                        style = MaterialTheme.typography.displayMedium
                    )

                    Text(
                        text = "Estimated flood chance",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HorizontalDivider()

                    Text(
                        text = "What does this mean?",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = riskExplanation,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Important: This is a hypothetical estimate based " +
                        "on historical rainfall patterns and approximate " +
                        "county-level inputs. It is not a live forecast, " +
                        "an official warning, or a guarantee of safety.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (inputsChanged) {
            Text(
                text = "Inputs have changed. Tap Calculate Risk " +
                        "to update your scenario prediction.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

    }
}
