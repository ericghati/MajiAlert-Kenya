package com.majialert.kenya.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.majialert.kenya.data.model.FloodAlert
import com.majialert.kenya.data.repository.AlertRepository
import com.majialert.kenya.data.repository.CountyRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun HomeScreen(
    countyName: String = "Nairobi",
    countyCode: String = "KE047",
    onNavigateToMap: () -> Unit = {},
    onNavigateToCounties: () -> Unit = {}

)

{

    var riskData by remember(countyCode) {
        mutableStateOf<Map<String, Any?>?>(null)
    }


    var alerts by remember {
        mutableStateOf<List<FloodAlert>>(emptyList())
    }

    var alertsLoading by remember {
        mutableStateOf(true)
    }

    var alertsError by remember {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(Unit) {
        val listener = AlertRepository.observeActiveAlerts(
            onSuccess = { result ->
                alerts = result
                alertsError = null
                alertsLoading = false
            },
            onError = { error ->
                alertsError = error.message ?: "Unable to load alerts"
                alertsLoading = false
            }
        )

        onDispose {
            listener.remove()
        }
    }

    var isLoading by remember(countyCode) {
        mutableStateOf(true)
    }

    var errorMessage by remember(countyCode) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(countyCode) {
        isLoading = true
        riskData = null
        errorMessage = null

        CountyRepository.getCountyRisk(
            countyCode = countyCode,
            onSuccess = { data ->
                riskData = data
                isLoading = false
            },
            onError = { error ->
                errorMessage = error.message ?: "Connection failed"
                isLoading = false
            }
        )
    }

    val riskBand = riskData?.get("riskBand")
        ?.toString()
        ?.replaceFirstChar { it.uppercase() }
        ?: "Not Available"

    val source = riskData?.get("source")
        ?.toString()
        ?: "Not Available"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Dashboard heading
        Text(
            text = "MajiAlert Kenya",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Stay informed. Stay safe.",
            style = MaterialTheme.typography.bodyMedium
        )

        // County information
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Selected County",
                    style = MaterialTheme.typography.labelLarge
                )

                Text(
                    (countyName),
                    style = MaterialTheme.typography.headlineSmall
                )

                Text("Selected from Kenya's 47 counties")

                OutlinedButton(
                    onClick = onNavigateToCounties
                ) {
                    Text("Change County")
                }
            }
        }

        // Flood risk information

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
                        Text("Loading county risk...")
                    }

                    errorMessage != null -> {
                        Text("Unable to load risk data")
                        Text(errorMessage ?: "Unknown error")
                    }

                    riskData == null -> {
                        Text("No risk data available")
                    }

                    else -> {
                        Text(
                            riskBand,
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Text("Source: $source")

                        val predictionDate = riskData?.get("asOf") as? Timestamp

                        Text(
                            text = "Model prediction date: " +
                                    formatAlertDate(predictionDate),
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            "Historical/model data. " +
                                    "Not a verified live flood warning.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }


        // Recent alerts
        // Historical flood alerts
        Text(
            text = "Historical Flood Risk Alerts",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = "Demo data from a historical model replay. " +
                    "These are not current flood warnings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            alertsLoading -> {
                CircularProgressIndicator()
                Text("Loading flood risk alerts...")
            }

            alertsError != null -> {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Unable to load alerts: $alertsError",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            alerts.isEmpty() -> {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No active alerts in the demo dataset.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            else -> {
                Text(
                    text = "${alerts.size} active alerts in historical replay",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Display alerts for the currently selected county
                val countyAlerts = alerts.filter {
                    it.county == countyCode
                }

                if (countyAlerts.isEmpty()) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No model alerts recorded for $countyName.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    countyAlerts.forEach { alert ->

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = countyName,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = "Risk severity: " +
                                            alert.severity.replaceFirstChar {
                                                it.uppercase()
                                            },
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                Text(
                                    text = alert.message,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    text = "Alert issued: " +
                                            formatAlertDate(alert.issuedAt),
                                    style = MaterialTheme.typography.bodySmall
                                )

                                if (alert.reasons.isNotEmpty()) {
                                    Text(
                                        text = "Model reasoning:",
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    alert.reasons.forEach { reason ->
                                        Text(
                                            text = "• $reason",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Text(
                                    text = if (alert.source.equals(
                                            "model",
                                            ignoreCase = true
                                        )
                                    ) {
                                        "A model estimate, not an official forecast."
                                    } else {
                                        "Historical source: ${alert.source}. " +
                                                "Not a verified current warning."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick navigation
        Text(
            "Quick Access",
            style = MaterialTheme.typography.titleLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigateToMap,
                modifier = Modifier.weight(1f)
            ) {
                Text("Flood Map")
            }

            OutlinedButton(
                onClick = onNavigateToCounties,
                modifier = Modifier.weight(1f)
            ) {
                Text("Counties")
            }
        }

        Text(
            "Monitoring Kenya's 47 counties",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatAlertDate(timestamp: Timestamp?): String {
    if (timestamp == null) return "Date unavailable"

    val formatter = SimpleDateFormat(
        "dd MMM yyyy",
        Locale.ENGLISH
    )
    formatter.timeZone = TimeZone.getTimeZone("UTC")

    return formatter.format(timestamp.toDate())
}
