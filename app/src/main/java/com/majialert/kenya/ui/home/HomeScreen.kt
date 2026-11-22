
package com.majialert.kenya.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import com.majialert.kenya.data.repository.CountyRepository

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
        Text(
            "Recent Flood Alerts",
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("No live alerts loaded")

                Spacer(Modifier.height(8.dp))

                Text(
                    "Live alerts have not been loaded. " +
                            "Check official emergency updates for current warnings."
                )
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
