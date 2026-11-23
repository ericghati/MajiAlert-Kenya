
package com.majialert.kenya.ui.counties

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.majialert.kenya.data.model.County
import androidx.compose.runtime.*
import com.majialert.kenya.data.repository.CountyRepository
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.majialert.kenya.data.util.RiskFormatter



private fun formatFirestoreDate(value: Any?): String {
    if (value !is Timestamp) {
        return "Not available"
    }

    val formatter = SimpleDateFormat(
        "dd MMM yyyy, HH:mm 'UTC'",
        Locale.ENGLISH
    )

    formatter.timeZone = TimeZone.getTimeZone("UTC")

    return formatter.format(value.toDate())
}


@Composable
fun CountyDetailsScreen(
    county: County,
    onBack: () -> Unit
) {



    var countyData by remember(county.code) {
        mutableStateOf<Map<String, Any?>?>(null)
    }

    var isLoading by remember(county.code) {
        mutableStateOf(true)
    }

    var errorMessage by remember(county.code) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(county.code) {
        isLoading = true
        countyData = null
        errorMessage = null

        CountyRepository.getCountyRisk(
            countyCode = county.code,
            onSuccess = { data ->
                countyData = data
                isLoading = false
            },
            onError = { error ->
                errorMessage = error.message ?: "Unable to load county data"
                isLoading = false
            }
        )
    }




    val riskBand = countyData
        ?.get("riskBand")
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.replaceFirstChar { it.uppercase() }
        ?: "Not Available"

    LaunchedEffect(countyData) {
        if (countyData != null) {
            android.util.Log.d(
                "MAJIALERT_DEBUG",
                "County: ${county.name}, " +
                        "Risk: ${countyData?.get("riskBand")}, " +
                        "Chance: ${countyData?.get("chance")}, " +
                        "Reasons: ${countyData?.get("reasons")}"
            )
        }
    }

    val chance = (countyData?.get("chance") as? Number)
        ?.toDouble()


    val reasons = (countyData?.get("reasons") as? List<*>)
        ?.filterIsInstance<String>()
        ?.filter { it.isNotBlank() }
        ?: emptyList()


    val source = countyData?.get("source")
        ?.toString()
        ?: "Not Available"

    val asOf = formatFirestoreDate(
        countyData?.get("asOf")
    )

    val updatedAt = formatFirestoreDate(
        countyData?.get("updatedAt")
    )


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Back to Counties")
        }

        Text(
            text = "${county.name} County",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("County code: ${county.code}")

        Text(
            text = when {
                isLoading -> "Firestore: Loading..."
                errorMessage != null ->
                    "Firestore error: $errorMessage"
                countyData == null ->
                    "Firestore: No document found"
                else ->
                    "Firestore fields: ${countyData?.keys?.joinToString()}"
            },
            style = MaterialTheme.typography.bodySmall
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Flood Risk Status",
                    style = MaterialTheme.typography.titleLarge
                )

                when {
                    isLoading -> {
                        CircularProgressIndicator()
                        Text("Loading flood-risk information...")
                    }

                    errorMessage != null -> {
                        Text("Unable to load flood-risk data")
                        Text(errorMessage ?: "Unknown error")
                    }

                    countyData == null -> {
                        Text("No flood-risk data available")
                    }

                    else -> {
                        Text(
                            riskBand,
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Text(
                            "Reported category from the " +
                                    "connected data source."
                        )
                    }
                }
            }
        }



        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Monitoring Information",
                    style = MaterialTheme.typography.titleLarge
                )

                if (isLoading) {
                    Text("Loading information...")
                } else if (errorMessage != null || countyData == null) {
                    Text("Monitoring information unavailable")
                } else {
                    Text("Source: $source")
                    Text("Prediction date: $asOf")
                    Text("Record updated: $updatedAt")

                    Text(
                        text = "Estimated flood chance: ${RiskFormatter.formatChance(chance)}",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        "Risk categories may be model-generated " +
                                "and should not be treated as official " +
                                "emergency alerts."
                    )

                    Text(
                        "Historical model prediction — not a live flood alert.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }


        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Why this risk level?",
                    style = MaterialTheme.typography.titleLarge
                )

                when {
                    isLoading -> {
                        Text("Loading risk explanations...")
                    }

                    errorMessage != null || countyData == null -> {
                        Text("Risk explanations unavailable.")
                    }

                    reasons.isEmpty() -> {
                        Text(
                            "No model explanation is available " +
                                    "for this county."
                        )
                    }

                    else -> {
                        reasons.forEachIndexed { index, reason ->
                            Text("${index + 1}. $reason")
                        }
                    }
                }

                Text(
                    text = "Explanations are based on historical " +
                            "model inputs, not live flood monitoring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Safety Reminder",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Avoid flooded roads and waterways. " +
                            "Follow official emergency instructions."
                )
            }
        }
    }
}
