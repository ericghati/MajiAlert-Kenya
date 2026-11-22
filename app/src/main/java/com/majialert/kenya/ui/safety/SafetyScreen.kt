
package com.majialert.kenya.ui.safety

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SafetyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Flood Safety Guide",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Practical steps to help you stay safe " +
                    "before, during and after flooding."
        )

        SafetyCard(
            title = "Before a Flood",
            tips = listOf(
                "Follow official weather and flood updates.",
                "Identify safer routes and higher ground.",
                "Prepare essential supplies and medication.",
                "Keep important documents protected.",
                "Discuss an emergency plan with your household."
            )
        )

        SafetyCard(
            title = "During a Flood",
            tips = listOf(
                "Move away from rising floodwater.",
                "Never walk or drive through flooded roads.",
                "Follow evacuation instructions from authorities.",
                "Avoid fallen electrical cables.",
                "Keep children away from floodwater."
            )
        )

        SafetyCard(
            title = "After a Flood",
            tips = listOf(
                "Return only when authorities say it is safe.",
                "Avoid damaged buildings and electrical equipment.",
                "Use safe drinking water.",
                "Stay away from contaminated floodwater.",
                "Continue monitoring official safety updates."
            )
        )

        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Important Reminder",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "MajiAlert provides flood-risk information " +
                            "and safety guidance. Always follow " +
                            "official emergency instructions."
                )
            }
        }
    }
}

@Composable
private fun SafetyCard(
    title: String,
    tips: List<String>
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge
            )

            tips.forEach { tip ->
                Text("• $tip")
            }
        }
    }
}
