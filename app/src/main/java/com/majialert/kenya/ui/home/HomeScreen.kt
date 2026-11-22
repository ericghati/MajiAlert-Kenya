
package com.majialert.kenya.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    countyName: String = "Nairobi",
    onNavigateToMap: () -> Unit = {},
    onNavigateToCounties: () -> Unit = {}
) {
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

                Text(
                    "Demonstration selection"
                )

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

                Text(
                    "Not Available",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    "Live flood-risk information has not " +
                            "been connected yet."
                )

                Text(
                    "Data source: Pending",
                    style = MaterialTheme.typography.bodySmall
                )
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
                    "Alerts will appear here once " +
                            "the backend is connected."
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
