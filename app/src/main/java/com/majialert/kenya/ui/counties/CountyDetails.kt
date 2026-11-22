
package com.majialert.kenya.ui.counties

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.majialert.kenya.data.model.County


@Composable
fun CountyDetailsScreen(
    county: County,
    onBack: () -> Unit
) {
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
                    "Current flood-risk information " +
                            "has not been loaded for this county."
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
                    "Monitoring Information",
                    style = MaterialTheme.typography.titleLarge
                )

                Text("Data source: Pending")
                Text("Last updated: Not available")
                Text("Flood probability: Not available")
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
